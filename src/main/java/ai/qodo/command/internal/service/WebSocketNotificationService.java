/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.service;

import ai.qodo.command.internal.api.*;
import ai.qodo.command.internal.config.MCPClientInitializer;
import ai.qodo.command.internal.config.QodoProperties;
import ai.qodo.command.internal.mcp.AgentCommand;
import ai.qodo.command.internal.mcp.AgentMcpServers;
import ai.qodo.command.internal.mcp.McpServerInitialized;
import ai.qodo.command.internal.metrics.McpMetrics;
import ai.qodo.command.internal.metrics.WebSocketMetrics;
import ai.qodo.command.internal.pojo.CommandSession;
import ai.qodo.command.internal.pojo.CommandSessionBuilder;
import ai.qodo.command.internal.pojo.ServerRawResponses;
import ai.qodo.command.internal.transformer.TemplateProcessor;
import ai.qodo.command.internal.util.WebSocketUtil;
import io.micrometer.core.instrument.Timer;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Service responsible for sending WebSocket notifications for Snyk webhook events.
 * This service handles the complete lifecycle of WebSocket notifications including
 * connection management, message formatting, and response handling.
 */
@Service("websocketNotificationService")
@Scope("prototype")
public class WebSocketNotificationService implements MessageService, BeanNameAware {
    public static final String TYPE_STRUCTURED_OUTPUT = "structured_output";
    private static final Logger logger = LoggerFactory.getLogger(WebSocketNotificationService.class);
    private final WebSocketService webSocketService;
    private final MCPClientInitializer mcpClientInitializer;
    private final McpMetrics mcpMetrics;
    private final List<TaskResponse> allTaskResponses = new ArrayList<>();
    private final TemplateProcessor templateProcessor;
    private final QodoProperties qodoProperties;
    private final ApplicationContext applicationContext;
    private final CountDownLatch completionLatch = new CountDownLatch(1);
    private final Object readySignalLock = new Object();
    private final WebSocketMetrics webSocketMetrics;
    private CommandSession commandSession;
    private volatile CompletableFuture<Void> readySignal;
    private volatile boolean readySignalPending = false;
    private volatile Instant readySignalStartTime;
    private volatile boolean isReconnecting = false;
    private String serviceKey;

    @Autowired
    public WebSocketNotificationService(WebSocketService webSocketService, MCPClientInitializer mcpClientInitializer,
                                        TemplateProcessor templateProcessor, QodoProperties qodoProperties,
                                        ApplicationContext applicationContext, McpMetrics mcpMetrics,
                                        WebSocketMetrics webSocketMetrics) {
        this.webSocketService = webSocketService;
        this.mcpClientInitializer = mcpClientInitializer;
        this.templateProcessor = templateProcessor;
        this.qodoProperties = qodoProperties;
        this.applicationContext = applicationContext;
        this.mcpMetrics = mcpMetrics;
        this.webSocketMetrics = webSocketMetrics;
    }

    @Override
    public void setBeanName(String name) {
        this.serviceKey = name;
    }

    /**
     * Sends a WebSocket notification for the given Snyk webhook payload.
     * This method blocks until the WebSocket work is complete (ENDNODE received).
     *
     */
    @Override
    public void process() {
        if (!isWebSocketTokenConfigured()) {
            logger.warn("No WebSocket token configured, cannot send notifications");
            return;
        }

        try {
            logger.info("Executing WebSocket send for event: {}", commandSession.eventKey());

            // Execute the WebSocket send and wait for connection/send to complete or fail
            CompletableFuture<Void> future = executeWebSocketSend(commandSession);

            // This will throw ExecutionException if connection fails (including reconnection failures)
            future.get(qodoProperties.getWebsocket().getConnectionTimeoutSeconds(), TimeUnit.SECONDS);

            // Block until WebSocket work is complete
            logger.debug("Waiting for WebSocket work to complete for event with key: {} and session id {}",
                         commandSession.eventKey(), commandSession.sessionId());
            completionLatch.await();
            logger.info("WebSocket work completed for event: {}", commandSession.eventKey());

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();

            // Log accumulated responses before throwing
            logAccumulatedResponses("ExecutionException");

            // Check if it's a CommandException from reconnection failure
            if (cause instanceof CommandException) {
                logger.error("WebSocket connection failed with CommandException for event: {} - {}",
                             commandSession.eventKey(), cause.getMessage());
                completionLatch.countDown();
                // Re-throw to trigger JMS rollback
                throw (CommandException) cause;
            }

            // Wrap other exceptions
            logger.error("WebSocket operation failed for event: {}", commandSession.eventKey(), cause);
            completionLatch.countDown();
            throw new CommandException("WebSocket operation failed: " + cause.getMessage(), cause);

        } catch (TimeoutException e) {
            // Log accumulated responses before throwing
            logAccumulatedResponses("TimeoutException");

            logger.error("WebSocket operation timed out after {}s for event: {}", qodoProperties
                    .getWebsocket()
                    .getConnectionTimeoutSeconds(), commandSession.eventKey());
            completionLatch.countDown();
            throw new CommandException("WebSocket operation timed out", e);

        } catch (InterruptedException e) {
            // Log accumulated responses before throwing
            logAccumulatedResponses("InterruptedException");

            Thread.currentThread().interrupt();
            logger.error("WebSocket operation was interrupted for event: {}", commandSession.eventKey());
            completionLatch.countDown();
            throw new CommandException("WebSocket operation interrupted", e);
        }
    }

    @Override
    public void init(CommandSession commandSession) {
        this.commandSession = commandSession;
        // Initialize ready signal early to prevent race conditions
        initializeReadySignal();
        logger.debug("Initialized WebSocketNotificationService for session: {}", commandSession.sessionId());
    }

    @Override
    public String serviceKey() {
        return serviceKey;
    }

    /**
     * Initializes a new ready signal with proper synchronization.
     * This method ensures thread-safe initialization and prevents race conditions.
     */
    private void initializeReadySignal() {
        synchronized (readySignalLock) {
            if (readySignal == null || readySignal.isDone()) {
                readySignal = new CompletableFuture<>();
                readySignalPending = true;
                logger.debug("Initialized new ready signal for session: {}", commandSession != null ?
                        commandSession.sessionId() : "unknown");
            } else {
                logger.warn("Attempted to initialize ready signal while one is still pending for session: {}",
                            commandSession != null ? commandSession.sessionId() : "unknown");
            }
        }
    }

    /**
     * Completes the ready signal with proper synchronization and validation.
     *
     * @return true if signal was completed, false if it was already done or null
     */
    private boolean completeReadySignal() {
        synchronized (readySignalLock) {
            if (readySignal != null && !readySignal.isDone() && readySignalPending) {
                readySignal.complete(null);
                readySignalPending = false;

                logger.debug("Ready signal completed for session: {}", commandSession != null ?
                        commandSession.sessionId() : "unknown");

                return true;
            } else {
                if (readySignal == null) {
                    logger.debug("Attempted to complete null ready signal for session: {}", commandSession != null ?
                            commandSession.sessionId() : "unknown");
                } else if (readySignal.isDone()) {
                    logger.debug("Attempted to complete already-done ready signal for session: {}",
                                 commandSession != null ? commandSession.sessionId() : "unknown");
                } else if (!readySignalPending) {
                    logger.debug("Attempted to complete ready signal that is not pending for session: {}",
                                 commandSession != null ? commandSession.sessionId() : "unknown");
                }
                return false;
            }
        }
    }

    /**
     * Completes the ready signal exceptionally with proper synchronization.
     *
     * @param throwable The exception to complete with
     * @return true if signal was completed exceptionally, false if it was already done or null
     */
    private boolean completeReadySignalExceptionally(Throwable throwable) {
        synchronized (readySignalLock) {
            if (readySignal != null && !readySignal.isDone() && readySignalPending) {
                readySignal.completeExceptionally(throwable);
                readySignalPending = false;
                logger.error("Ready signal completed exceptionally for session: {}", commandSession != null ?
                        commandSession.sessionId() : "unknown", throwable);
                return true;
            } else {
                logger.warn("Attempted to complete null or already-done ready signal exceptionally for session: {}",
                            commandSession != null ? commandSession.sessionId() : "unknown");
                return false;
            }
        }
    }

    /**
     * Resets the ready signal for the next cycle with validation.
     * Ensures the previous signal was completed before resetting.
     *
     * @param reason The reason for resetting (for logging)
     */
    private void resetReadySignal(String reason) {
        synchronized (readySignalLock) {
            if (readySignal != null && !readySignal.isDone()) {
                logger.warn("Resetting ready signal that is not yet completed for session: {} (reason: {})",
                            commandSession != null ? commandSession.sessionId() : "unknown", reason);
            }

            readySignal = new CompletableFuture<>();
            readySignalPending = true;
            logger.debug("Reset ready signal for session: {} (reason: {})", commandSession != null ?
                    commandSession.sessionId() : "unknown", reason);
        }
    }

    /**
     * Handles reconnection start by setting the reconnection flag and resetting the ready signal.
     * This prevents the error handler from interfering with the reconnection process.
     */
    private void handleReconnectionStart() {
        logger.info("Reconnection starting for session: {} - setting reconnection flag and resetting ready signal",
                    commandSession != null ? commandSession.sessionId() : "unknown");
        isReconnecting = true;

        // Reset the ready signal for the reconnection attempt
        synchronized (readySignalLock) {
            if (readySignal != null && !readySignal.isDone()) {
                logger.debug("Completing pending ready signal before reconnection for session: {}",
                             commandSession != null ? commandSession.sessionId() : "unknown");
                // Complete it exceptionally to unblock any waiting threads
                readySignal.completeExceptionally(new CommandException("Reconnection in progress"));
            }
            // Create a new ready signal for the reconnected session
            readySignal = new CompletableFuture<>();
            readySignalPending = true;
            logger.debug("Created new ready signal for reconnection attempt for session: {}", commandSession != null
                    ? commandSession.sessionId() : "unknown");
        }
    }

    /**
     * Handles reconnection completion by clearing the reconnection flag.
     */
    private void handleReconnectionComplete() {
        logger.info("Reconnection completed for session: {} - clearing reconnection flag", commandSession != null ?
                commandSession.sessionId() : "unknown");
        isReconnecting = false;
    }

    /**
     * Executes the actual WebSocket connection and message send
     */
    private CompletableFuture<Void> executeWebSocketSend(CommandSession session) {
        logger.debug("Building agent request for event: {}", session.eventKey());

        // Validate that agentCommand is present
        if (session.agentCommand() == null) {
            String error = String.format("No agent command configured for message type '%s' in session '%s'. " +
                                                 "Please verify that the message type is defined in the agent " +
                                                 "configuration file.", session.messageType(), session.sessionId());
            logger.error(error);
            return CompletableFuture.failedFuture(new CommandException(error));
        }

        // Ensure ready signal is initialized (should already be done in init())
        if (readySignal == null) {
            logger.warn("Ready signal was null in executeWebSocketSend, initializing now for session: {}",
                        session.sessionId());
            initializeReadySignal();
        }

        return CompletableFuture.supplyAsync(() -> {
            String instructionTemplate = session.agentCommand().instructions();
            String instructions = templateProcessor.processTemplate(instructionTemplate, session.payload());
            logger.debug("Processed instructions for event: {}", session.eventKey());
            if(logger.isTraceEnabled()) {
                logger.trace("Instructions:\n{}\n", instructions);
            }
            // Build agent request
            AgentRequest agentRequest = buildAgentRequestForService(session.sessionId(), instructions,
                                                                    session.agentCommand());
            logger.debug("Request to server is {}", agentRequest);
            return agentRequest;
        }).thenCompose(agentRequest -> {
            // Connect to WebSocket
            logger.debug("Connecting to WebSocket for event: {}", session.eventKey());
            return webSocketService
                    .connect(session, qodoProperties
                            .getWebsocket()
                            .getToken(), taskResponse -> handle(session, taskResponse),
                             error -> handleWebSocketError(session, error))
                    .thenCompose(webSocket -> {
                        if (webSocket == null) {
                            logger.error("WebSocket connection returned null for event: {}", session.eventKey());
                            return CompletableFuture.failedFuture(new IllegalStateException("WebSocket connection " + "failed"));
                        }
                        // Start timing the READY signal wait
                        readySignalStartTime = Instant.now();
                        long timeoutSeconds = qodoProperties.getWebsocket().getReadySignalTimeoutSeconds();

                        logger.info("WebSocket connected, waiting for READY signal (timeout: {}s) before sending " +
                                            "agent request for session: {}", timeoutSeconds, session.sessionId());

                        // Wait for the READY signal with configurable timeout
                        return readySignal.orTimeout(timeoutSeconds, TimeUnit.SECONDS).thenCompose(v -> {
                            // Calculate and record wait time
                            Duration waitDuration = Duration.between(readySignalStartTime, Instant.now());
                            webSocketMetrics.getReadySignalWaitTimer().record(waitDuration);

                            logger.info("Received READY signal after {}ms, now sending agent request for session: {}"
                                    , waitDuration.toMillis(), session.sessionId());

                            // Send the agent request with explicit session ID
                            return webSocketService.sendObject(WireMsgRouteKey.UserQuery, session.sessionId(),
                                                               agentRequest);
                        }).exceptionally(throwable -> {
                            // Unwrap CompletionException if present
                            Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;

                            if (cause instanceof TimeoutException) {
                                Duration waitDuration = Duration.between(readySignalStartTime, Instant.now());
                                webSocketMetrics.recordReadySignalTimeout();

                                logger.error("Timeout waiting for READY signal after {}ms for session: {} " +
                                                     "(configured timeout: {}s)", waitDuration.toMillis(),
                                             session.sessionId(), timeoutSeconds);

                                throw new CommandException(String.format("Timeout waiting for server READY signal " + "after %dms (timeout: %ds)", waitDuration.toMillis(), timeoutSeconds));
                            }

                            logger.error("Error while waiting for READY signal for session: {} - {}",
                                         session.sessionId(), cause.getMessage(), cause);
                            throw new CommandException("Failed to receive READY signal: " + cause.getMessage(), cause);
                        });
                    });
        }).thenAccept(result -> {
            logger.info("Successfully composed WebSocket for event: {} ", session.eventKey());
        });
    }

    private void handle(CommandSession session, TaskResponse taskResponse) {
        logger.debug("Received WebSocket response for session {} event {}: {}", session.sessionId(),
                     session.eventKey(), taskResponse);

        if (taskResponse.error() != null) {
            logger.error("WebSocket response error for session {} event {}: {}", session.sessionId(),
                         session.eventKey(), taskResponse.error());
            return;
        }

        if (taskResponse.data() == null) {
            logger.warn("WebSocket response has no data for session {} event: {} taskResponse {}",
                        session.sessionId(), session.eventKey(), taskResponse);
            return;
        }

        ToolData toolData = taskResponse.data();
        String value = toolData.tool() != null ? toolData.tool().toUpperCase() : "NA";

        switch (value) {
            case "USERRESPONSE":
                logger.debug("AI Analysis for event {}: {}", session.eventKey(), taskResponse);
                allTaskResponses.add(taskResponse);
                break;
            case "ENDNODE":
                logger.info("WebSocket notification completed for session {} event: {}", session.sessionId(),
                            session.eventKey());

                // Mark that we expect the server to close the connection
                webSocketService.markExpectedClose();

                // Invoke handler with session-specific responses
                Handler handler = lookupService(session.agentCommand().name() + Handler.HANDLER_SUFFIX, Handler.class);
                handler.handle(session, List.copyOf(allTaskResponses));

                logger.debug("Cleaned up session data for session: {}", session.sessionId());

                completionLatch.countDown();

                if (logger.isTraceEnabled()) {
                    ServerRawResponses rawResponses = WebSocketUtil.parseTaskResponses(allTaskResponses);
                    logger.trace("Structured response: {}", rawResponses.structuredJson());
                    logger.trace("Unstructured conversation from server: {}", rawResponses.unstructuredJson());
                }

                break;
            case "THINKING":
                logger.debug("Thinking for session {}: {}", session.sessionId(), taskResponse);
                break;
            case "REVIEWER_NOTES":
                logger.info("Reviewer notes for session {}: {}", session.sessionId(), taskResponse);
                break;
            case "READY":
                logger.info("Server READY response: sessionId={}, checkpointId={}, previousCheckpointId={}",
                            session.sessionId(), taskResponse
                        .data()
                        .checkpointId(), commandSession.checkPointId());

                // Update checkpoint_id from READY event
                String newCheckpointId = taskResponse.data().checkpointId();
                if (newCheckpointId != null && !newCheckpointId.equals(commandSession.checkPointId())) {
                    logger.debug("Updating checkpoint_id: {} -> {}", commandSession.checkPointId(), newCheckpointId);
                    commandSession = CommandSessionBuilder
                            .withUpdatedCheckpoint(commandSession, newCheckpointId)
                            .build();
                    logger.info("Reconstructed CommandSession with new checkpoint_id: {} for session: {}",
                                newCheckpointId, session.sessionId());
                } else {
                    logger.debug("Checkpoint_id unchanged or null: {}", newCheckpointId);
                }

                // Record READY signal received metric
                webSocketMetrics.recordReadySignalReceived();

                // Complete the ready signal to unblock message sending using synchronized method
                boolean completed = completeReadySignal();
                if (!completed) {
                    logger.debug("Received READY signal but ready signal was already completed for session: {}",
                                 session.sessionId());
                } else {
                    // Log timing information if available
                    if (readySignalStartTime != null) {
                        Duration waitDuration = Duration.between(readySignalStartTime, Instant.now());
                        logger.debug("READY signal received after {}ms for session: {}", waitDuration.toMillis(),
                                     session.sessionId());
                    }
                }
                break;
            default:
                invokeTool(session, taskResponse);
                break;
        }


    }


    protected void handleWebSocketError(CommandSession session, String error) {
        // Check if this is an expected 1006 closure (after ENDNODE)
        // The WebSocketService marks expectedClose when ENDNODE is received
        boolean isExpected1006 = error != null && error.contains("Connection closed abnormally: 1006");

        if (isExpected1006) {
            // Treat as normal closure - just log at INFO level
            logger.info("WebSocket connection closed normally for session {} (server-initiated 1006 after ENDNODE)",
                        session.sessionId());

            // Complete the latch to unblock the waiting thread and allow normal completion
            // This ensures the process() method completes successfully without reconnection
            completionLatch.countDown();
            logger.debug("Completion latch counted down for expected 1006 closure for session: {}",
                         session.sessionId());

            // Don't log accumulated responses or complete ready signal exceptionally
            // This is expected behavior after ENDNODE - just complete normally
            return;
        }

        // For all other errors, log as error
        logger.error("WebSocket error for session {}: {} [checkpoint_id={}, attempt={}, reconnecting={}, " +
                             "ready_pending={}]", session.sessionId(), error, session.checkPointId(),
                     session.attemptCount(), isReconnecting, readySignalPending);

        if (logger.isDebugEnabled()) {
            // Log accumulated responses when error occurs
            if (error != null && error.contains("1006")) {
                logger.warn("Abnormal connection closure (1006) detected - logging accumulated responses");
                logAccumulatedResponses("WebSocket-Error-1006");
            } else {
                // Log for other errors too, but with different context
                logAccumulatedResponses("WebSocket-Error");
            }
        }

        // Check if we're in a reconnection scenario
        if (isReconnecting) {
            logger.debug("WebSocket error occurred during reconnection for session: {} - " + "skipping ready signal " + "completion to allow reconnection to proceed", session.sessionId());
            return;
        }

        // Complete the ready signal exceptionally if it's still pending using synchronized method
        // Only do this if we're not reconnecting
        boolean completed =
                completeReadySignalExceptionally(new CommandException("WebSocket error before READY: " + error));
        if (!completed) {
            logger.debug("Ready signal was already completed when WebSocket error occurred for session: {}",
                         session.sessionId());
        }

        // DO NOT throw exception here - it prevents WebSocketService retry logic from running
        // The exception is already propagated through completeReadySignalExceptionally()
        // which will cause the CompletableFuture chain to fail appropriately
        logger.warn("WebSocket error handler completed for session: {} - retry logic will be handled by " +
                            "WebSocketService", session.sessionId());
    }

    /**
     * Logs all accumulated task responses for diagnostic purposes.
     * Safely handles null values and separates structured/unstructured content.
     *
     * @param context Additional context about when this logging occurs
     */
    private void logAccumulatedResponses(String context) {
        ServerRawResponses responses = WebSocketUtil.parseTaskResponses(allTaskResponses);
        logger.warn("""
                            Following Error Type Context {}\s
                             Structured response from server: {}\s
                             Unstructured \
                            conversation from server: {}""", context, responses.structuredJson(),
                    responses.unstructuredJson());
    }

    /**
     * Sends a tool response back to the WebSocket.
     *
     * @param sessionId The session ID to send the response to
     * @param toolData  The tool data to respond to
     * @param result    The tool call result
     */
    private void sendToolResponse(String sessionId, ToolData toolData, McpSchema.CallToolResult result,
                                  List<McpSchema.Tool> tools) {
        ToolResponseBuilder.ToolAnswerBuilder builder = ToolResponseBuilder.answer().isError(false);
        List<McpSchema.Content> contents = result.content();
        if (contents != null) {
            for (McpSchema.Content c : contents) {
                if (c instanceof McpSchema.TextContent) {
                    builder.addTextContent(((McpSchema.TextContent) c).text());
                    logger.debug("Tool response {}", ((McpSchema.TextContent) c).text());
                }

            }
        }

        ToolResponse response = new ToolResponseBuilder()
                .sessionId(sessionId)
                .tool(toolData.tool())
                .toolId(toolData.identifier())
                .tools(Map.of("IDETool", tools))
                .answer(builder.build())
                .build();

        // Reset the ready signal before sending the tool response to wait for the next READY from server
        resetReadySignal("tool response sent");
        logger.debug("Sending response from tool {}", response);
        webSocketService.sendObject(WireMsgRouteKey.IDERetrievalAnswer, sessionId, response);

    }

    /**
     * Sends a tool failed response back to the WebSocket.
     *
     * @param sessionId The session ID to send the response to
     * @param toolData  The tool data to respond to
     */
    private void sendToolFailedResponse(String sessionId, ToolData toolData) {
        ToolResponseBuilder.ToolAnswerBuilder builder = ToolResponseBuilder.answer().isError(true);
        ToolResponse response = new ToolResponseBuilder()
                .tool(toolData.tool())
                .toolId(toolData.identifier())
                .answer(builder.build())
                .build();

        // Reset the ready signal before sending the tool failed response to wait for the next READY from server
        resetReadySignal("tool failed response sent");

        webSocketService.sendObject(WireMsgRouteKey.IDERetrievalAnswer, sessionId, response);
    }

    /**
     * Sends a timeout-specific error response that doesn't cause disconnection.
     * Provides helpful guidance to the user about the timeout.
     *
     * @param sessionId The session ID to send the response to
     * @param toolData  The tool data that timed out
     */
    private void sendToolTimeoutResponse(String sessionId, ToolData toolData) {
        // Create a user-friendly timeout message
        String timeoutMessage =
                String.format("The command '%s' took too long and timed out (exceeded %d seconds). " + "Please try a " +
                                      "shorter command or break it into smaller steps. " + "For example, instead of " +
                                      "processing large datasets, try processing smaller chunks.", toolData.tool(),
                              qodoProperties
                .getMcp()
                .getRequestTimeoutSeconds());

        // Build the error response with isError=true and helpful content
        ToolResponseBuilder.ToolAnswerBuilder builder = ToolResponseBuilder
                .answer()
                .isError(true)
                .addTextContent(timeoutMessage);

        ToolResponse response = new ToolResponseBuilder()
                .sessionId(sessionId)
                .tool(toolData.tool())
                .toolId(toolData.identifier())
                .answer(builder.build())
                .build();

        // Log the timeout for monitoring
        logger.info("Sending timeout error response for tool {} in session {} - suggesting shorter commands",
                    toolData.tool(), sessionId);

        // Reset the ready signal before sending the response
        resetReadySignal("tool timeout response sent");

        // Send the response without closing the WebSocket
        webSocketService.sendObject(WireMsgRouteKey.IDERetrievalAnswer, sessionId, response);
    }


    /**
     * Builds an AgentRequest for the WebSocket session using the service approach.
     *
     * @param sessionId    The session ID
     * @param instructions The instructions for the AI agent
     * @return AgentRequest configured for the notification
     */
    private AgentRequest buildAgentRequestForService(String sessionId, String instructions, AgentCommand agentCommand) {
        try {
            AgentMcpServers servers = mcpClientInitializer.getAgentMcpServers(agentCommand.name());
            Map<String, McpServerInitialized> mcpServers = servers.mcpServers();

            AgentRequestBuilder builder = new AgentRequestBuilder();

            builder
                    .baseData()
                    .sessionId(sessionId)
                    .agentType("cli")
                    .userData()
                    .permissions("rwx")
                    .tools(mcpServers, agentCommand.tools());

            TaskBaseDataBuilder taskBaseDataBuilder = builder
                    .taskBaseData()
                    .systemPrompt(agentCommand.systemPrompt())
                    .instructions(instructions)
                    .cwd(System.getProperty("user.dir"))
                    .addProjectRootPath(System.getProperty("user.dir"));

            // Set project structure if available
            if (commandSession.projectStringStructure() != null) {
                taskBaseDataBuilder.projectStructure(commandSession.projectStringStructure());
                logger.debug("Setting project structure in AgentRequest for session: {}", sessionId);
            }

            builder
                    .taskRequestData()
                    .userRequest(instructions)
                    .executionStrategy("act")
                    .outputSchema(agentCommand.outputSchema());

            if (agentCommand.model() != null && !agentCommand.model().isBlank()) {
                builder.taskRequestData().customModel(agentCommand.model());
            }

            return builder.build();

        } catch (Exception e) {
            logger.error("Failed to build agent request", e);
            throw new RuntimeException("Failed to build agent request", e);
        }
    }


    // Utility methods for validation
    private boolean isWebSocketTokenConfigured() {
        String token = qodoProperties.getWebsocket().getToken();
        return token != null && !token.isEmpty();
    }


    /**
     * Invokes a tool by creating a fresh MCP client for the specific server.
     * This ensures each tool call has a clean client instance.
     *
     */
    private void invokeTool(CommandSession session, TaskResponse taskResponse) {
        ToolData toolData = taskResponse.data();
        AgentCommand command = session.agentCommand();
        String sessionId = session.sessionId();
        String serverName = toolData.serverName();
        String toolName = toolData.tool();

        if (command.mcpServers() == null || command.mcpServers().trim().isEmpty()) {
            logger.error("No MCP server configuration found for tool {} in session {}", toolData, sessionId);
            // Only record metrics if serverName and toolName are not null
            if (serverName != null && toolName != null) {
                mcpMetrics.recordToolFailure(serverName, toolName);
            }
            sendToolFailedResponse(sessionId, toolData);
            return;
        }

        // Validate serverName and toolName before recording metrics
        if (serverName == null || toolName == null) {
            logger.error("Invalid tool invocation: serverName={}, toolName={} for session {}", serverName, toolName,
                         sessionId);
            sendToolFailedResponse(sessionId, toolData);
            return;
        }

        // Record tool invocation
        mcpMetrics.recordToolInvocation(serverName, toolName);

        // Get timer for this tool
        Timer.Sample sample = Timer.start();

        try {
            logger.debug("Invoking session {} with taskResponse {}", session, taskResponse);
            McpSyncClient freshClient = session.mcpClients().get(serverName);

            if (freshClient == null) {
                logger.error("Failed to create fresh client for server: {} in session: {} toolName {}", serverName,
                             sessionId, toolName);
                // serverName and toolName are already validated as non-null at this point
                mcpMetrics.recordToolFailure(serverName, toolName);
                sendToolFailedResponse(sessionId, toolData);
                return;
            } else {
                int argsSize = toolData.toolArgs() != null ? toolData.toolArgs().size() : 0;
                logger.info("MCPServer: {}.{}.args({}) is initialized {} ", serverName, toolName, argsSize,
                            freshClient.isInitialized());
            }
            McpSchema.CallToolRequest callToolRequest = McpSchema.CallToolRequest
                    .builder()
                    .name(toolName)
                    .arguments(taskResponse.data().toolArgs())
                    .build();

            McpSchema.CallToolResult result = null;
            boolean isTimeout = false;

            try {
                // Wrap the blocking call with explicit timeout handling
                result = freshClient.callTool(callToolRequest);
            } catch (Exception e) {
                // Check if it's a timeout-related exception
                Throwable cause = e.getCause();
                if (cause instanceof TimeoutException || (e.getMessage() != null && e
                        .getMessage()
                        .toLowerCase()
                        .contains("timeout")) || (cause != null && cause.getMessage() != null && cause
                        .getMessage()
                        .toLowerCase()
                        .contains("timeout"))) {

                    logger.warn("Tool {} on server {} timed out after configured duration for session {}: {}",
                                toolName, serverName, sessionId, e.getMessage());

                    isTimeout = true;

                    // Record timeout metric
                    sample.stop(mcpMetrics.getToolExecutionTimer(serverName, toolName));
                    mcpMetrics.recordToolFailure(serverName, toolName);

                    // Send graceful timeout error response
                    sendToolTimeoutResponse(sessionId, toolData);
                    return;
                } else {
                    // Re-throw non-timeout exceptions to maintain existing error handling
                    throw e;
                }
            }

            // Record success and execution time (serverName and toolName are validated as non-null)
            sample.stop(mcpMetrics.getToolExecutionTimer(serverName, toolName));
            mcpMetrics.recordToolSuccess(serverName, toolName);

            sendToolResponse(sessionId, toolData, result, freshClient.listTools().tools());

        } catch (Exception e) {
            logger.error("Failed to invoke tool {} on server {} for session {}: {}", toolName, serverName, sessionId,
                         e.getMessage(), e);

            // Record failure and execution time (serverName and toolName are validated as non-null)
            sample.stop(mcpMetrics.getToolExecutionTimer(serverName, toolName));
            mcpMetrics.recordToolFailure(serverName, toolName);

            sendToolFailedResponse(sessionId, toolData);
        }
    }

    /**
     * Looks up a service by name from the Spring application context.
     *
     * @param serviceName The name of the service to look up
     * @return The service instance or null if not found
     */
    private <T> T lookupService(String serviceName, Class<T> clazz) {
        try {
            return applicationContext.getBean(serviceName, clazz);
        } catch (Exception e) {
            logger.debug("Service '{}' not found in application context for clazz {}", serviceName, clazz);
            throw new RuntimeException("Did not find service " + serviceName, e);
        }
    }

}