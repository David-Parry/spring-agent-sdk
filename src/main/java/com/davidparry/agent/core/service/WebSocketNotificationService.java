/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.service;

import com.davidparry.agent.core.api.*;
import com.davidparry.agent.core.config.MCPClientInitializer;
import com.davidparry.agent.core.config.QodoProperties;
import com.davidparry.agent.core.mcp.AgentCommand;
import com.davidparry.agent.core.mcp.AgentMcpServers;
import com.davidparry.agent.core.mcp.McpServerInitialized;
import com.davidparry.agent.core.metrics.McpMetrics;
import com.davidparry.agent.core.metrics.WebSocketMetrics;
import com.davidparry.agent.core.pojo.CommandSession;
import com.davidparry.agent.core.pojo.CommandSessionBuilder;
import com.davidparry.agent.core.pojo.ServerRawResponses;
import com.davidparry.agent.core.transformer.TemplateProcessor;
import com.davidparry.agent.core.util.WebSocketUtil;
import io.micrometer.core.instrument.Timer;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
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
    public static final String TYPE_ANSWER = "answer";
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

    // In-flight message tracking for reconnection retry
    private volatile String lastSentMessageType;      // "UserQuery" or "IDERetrievalAnswer"
    private volatile Object lastSentPayload;          // The actual payload object
    private volatile String lastSentToolId;           // For tool responses (debugging)
    private volatile boolean messageInFlight = false; // True if message sent, awaiting READY

    // Checkpoint tracking for duplicate prevention
    private volatile String lastKnownCheckpoint;      // Last checkpoint received from server
    private volatile String checkpointWhenSent;       // Checkpoint when message was sent

    // Retry limiting (max 3 retries per message)
    private static final int MAX_MESSAGE_RETRIES = 3;
    private volatile int messageRetryCount = 0;       // Current retry count for in-flight message

    // Session timeout tracking
    private volatile Instant sessionStartTime;
    private volatile ScheduledFuture<?> sessionTimeoutTask;
    private final ScheduledExecutorService sessionScheduler;

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
        
        // Initialize session scheduler for timeout management
        this.sessionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-timeout-scheduler");
            t.setDaemon(true);
            return t;
        });
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
        // Schedule session timeout
        scheduleSessionTimeout();
        logger.debug("Initialized WebSocketNotificationService for session: {} with timeout: {}s", 
                     commandSession.sessionId(), qodoProperties.getWebsocket().getTotalSessionTimeoutSeconds());
    }

    @Override
    public String serviceKey() {
        return serviceKey;
    }

    /**
     * Cleanup resources when service is destroyed.
     * Ensures proper shutdown of the session timeout scheduler to prevent thread leaks.
     */
    @PreDestroy
    public void destroy() {
        logger.debug("Destroying WebSocketNotificationService for session: {}", 
                     commandSession != null ? commandSession.sessionId() : "unknown");
        
        // Cancel any pending session timeout task
        cancelSessionTimeout();
        
        // Shutdown the session scheduler
        if (sessionScheduler != null && !sessionScheduler.isShutdown()) {
            sessionScheduler.shutdown();
            try {
                if (!sessionScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    sessionScheduler.shutdownNow();
                    if (!sessionScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        logger.warn("Session scheduler did not terminate for session: {}", 
                                   commandSession != null ? commandSession.sessionId() : "unknown");
                    }
                }
                logger.debug("Session scheduler shutdown complete for session: {}", 
                            commandSession != null ? commandSession.sessionId() : "unknown");
            } catch (InterruptedException e) {
                sessionScheduler.shutdownNow();
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while shutting down session scheduler for session: {}", 
                           commandSession != null ? commandSession.sessionId() : "unknown");
            }
        }
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
        logger.info("Reconnection starting for session: {} - messageInFlight={}, lastCheckpoint={}, retryCount={}",
                    commandSession != null ? commandSession.sessionId() : "unknown",
                    messageInFlight, lastKnownCheckpoint, messageRetryCount);
        
        isReconnecting = true;

        // Record metric for reconnection attempt
        webSocketMetrics.recordReconnectionAttempt();

        // Create fresh ready signal for reconnection
        synchronized (readySignalLock) {
            // Don't complete the old one exceptionally - just create a fresh one
            readySignal = new CompletableFuture<>();
            readySignalPending = true;
            readySignalStartTime = Instant.now();
            logger.debug("Created fresh ready signal for reconnection attempt for session: {}", 
                         commandSession != null ? commandSession.sessionId() : "unknown");
        }
        
        // Note: We keep lastSentPayload and messageInFlight intact for potential retry
        // The retry decision will be made in handleReconnectionSuccess() based on checkpoint comparison
    }

    /**
     * Handles successful reconnection.
     * The reconnection flag will be cleared in the READY handler after evaluating retry logic.
     * This ensures the READY handler knows this is the first READY after reconnection.
     */
    private void handleReconnectionSuccess() {
        logger.info("Reconnection successful for session: {} - messageInFlight={}, checkpointWhenSent={}, retryCount={}",
                    commandSession != null ? commandSession.sessionId() : "unknown",
                    messageInFlight, checkpointWhenSent, messageRetryCount);
        
        // NOTE: We intentionally do NOT clear isReconnecting here
        // It will be cleared in the READY handler after evaluating retry logic
        
        // Record metric for successful reconnection
        webSocketMetrics.recordReconnectionSuccess();
        
        // Message retry will be triggered by READY handler after checkpoint comparison
        // This ensures we have the latest checkpoint from server before deciding to retry
        if (messageInFlight && lastSentPayload != null) {
            logger.info("Message was in-flight during disconnection - will evaluate retry after READY signal");
        }
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
            // Connect to WebSocket with reconnection callbacks
            logger.debug("Connecting to WebSocket for event: {}", session.eventKey());
            return webSocketService
                    .connect(session, 
                             qodoProperties.getWebsocket().getToken(), 
                             taskResponse -> handle(session, taskResponse),
                             error -> handleWebSocketError(session, error),
                             this::handleReconnectionStart,
                             this::handleReconnectionSuccess)
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

                            // Track the initial UserQuery as in-flight
                            synchronized (readySignalLock) {
                                this.lastSentMessageType = "UserQuery";
                                this.lastSentPayload = agentRequest;
                                this.lastSentToolId = null;
                                this.checkpointWhenSent = null; // No checkpoint for initial request
                                this.messageInFlight = true;
                                this.messageRetryCount = 0; // Reset retry count for new message
                                logger.debug("Tracking in-flight UserQuery for session: {}", session.sessionId());
                            }

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

                // Cancel session timeout since session completed normally
                cancelSessionTimeout();

                // Mark that we expect the server to close the connection
                webSocketService.markExpectedClose();

                logger.info("Next Handler to call:'{}'", session.agentCommand().name() + Handler.HANDLER_SUFFIX);
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

                // Extract new checkpoint from READY
                String newCheckpointId = taskResponse.data().checkpointId();
                
                // Update lastKnownCheckpoint for tracking
                String previousKnownCheckpoint = this.lastKnownCheckpoint;
                this.lastKnownCheckpoint = newCheckpointId;
                
                // Update commandSession checkpoint if changed
                if (newCheckpointId != null && !newCheckpointId.equals(commandSession.checkPointId())) {
                    logger.debug("Updating checkpoint_id: {} -> {}", commandSession.checkPointId(), newCheckpointId);
                    commandSession = CommandSessionBuilder
                            .withUpdatedCheckpoint(commandSession, newCheckpointId)
                            .build();
                    logger.info("Reconstructed CommandSession with new checkpoint_id: {} for session: {}",
                                newCheckpointId, session.sessionId());
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

                // *** FIXED: Only evaluate retry after reconnection, not on every READY ***
                if (completed && messageInFlight && lastSentPayload != null) {
                    if (isReconnecting) {
                        // We just reconnected - evaluate if we need to retry the in-flight message
                        logger.debug("Evaluating retry for in-flight message after reconnection for session: {}", 
                                     session.sessionId());
                        evaluateAndRetryInFlightMessage(session.sessionId(), previousKnownCheckpoint, newCheckpointId);
                        isReconnecting = false;  // Clear the flag after handling
                    } else {
                        // Normal READY after successful send - just clear the in-flight state
                        logger.debug("Normal READY received after message send - clearing in-flight state for session: {}", 
                                     session.sessionId());
                        clearInFlightState();
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

        // For all other errors, log as error with comprehensive context
        logger.error("WebSocket error for session {}: {} [checkpoint_id={}, attempt={}, reconnecting={}, " +
                             "ready_pending={}, messageInFlight={}, retryCount={}, lastMessageType={}, lastCheckpoint={}]", 
                     session.sessionId(), error, session.checkPointId(),
                     session.attemptCount(), isReconnecting, readySignalPending,
                     messageInFlight, messageRetryCount, lastSentMessageType, lastKnownCheckpoint);

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

        // Track this message as in-flight BEFORE sending
        synchronized (readySignalLock) {
            this.lastSentMessageType = "IDERetrievalAnswer";
            this.lastSentPayload = response;
            this.lastSentToolId = toolData.identifier();
            this.checkpointWhenSent = this.lastKnownCheckpoint;
            this.messageInFlight = true;
            this.messageRetryCount = 0; // Reset retry count for new message
            logger.debug("Tracking in-flight tool response: toolId={}, checkpoint={}", 
                         toolData.identifier(), this.checkpointWhenSent);
        }

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

        // Track this message as in-flight
        synchronized (readySignalLock) {
            this.lastSentMessageType = "IDERetrievalAnswer";
            this.lastSentPayload = response;
            this.lastSentToolId = toolData.identifier();
            this.checkpointWhenSent = this.lastKnownCheckpoint;
            this.messageInFlight = true;
            this.messageRetryCount = 0; // Reset retry count for new message
            logger.debug("Tracking in-flight failed tool response: toolId={}", toolData.identifier());
        }

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

        // Track this message as in-flight
        synchronized (readySignalLock) {
            this.lastSentMessageType = "IDERetrievalAnswer";
            this.lastSentPayload = response;
            this.lastSentToolId = toolData.identifier();
            this.checkpointWhenSent = this.lastKnownCheckpoint;
            this.messageInFlight = true;
            this.messageRetryCount = 0; // Reset retry count for new message
        }

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

    /**
     * Evaluates whether to retry an in-flight message based on checkpoint comparison and retry limit.
     * Only retries if server state hasn't advanced (checkpoint unchanged) and retry limit not exceeded.
     *
     * @param sessionId The session ID
     * @param previousCheckpoint The checkpoint before reconnection
     * @param currentCheckpoint The checkpoint received in READY after reconnection
     */
    private void evaluateAndRetryInFlightMessage(String sessionId, String previousCheckpoint, String currentCheckpoint) {
        synchronized (readySignalLock) {
            if (!messageInFlight || lastSentPayload == null) {
                logger.debug("No in-flight message to retry for session: {}", sessionId);
                return;
            }

            // Check retry limit (max 3 retries)
            if (messageRetryCount >= MAX_MESSAGE_RETRIES) {
                logger.error("Max message retry limit ({}) exceeded for session: {} - abandoning retry",
                             MAX_MESSAGE_RETRIES, sessionId);
                webSocketMetrics.recordMessageRetryExhausted();
                clearInFlightState();
                
                // Complete ready signal exceptionally to propagate error
                completeReadySignalExceptionally(new CommandException(
                    "Max message retry limit exceeded after " + MAX_MESSAGE_RETRIES + " attempts"));
                return;
            }

            // Compare checkpoints to detect if server state has advanced
            boolean checkpointAdvanced = false;
            String comparisonResult = "unchanged";
            
            if (checkpointWhenSent == null && currentCheckpoint != null) {
                // Initial request - server has created a checkpoint, state advanced
                checkpointAdvanced = true;
                comparisonResult = "new_checkpoint_created";
                logger.info("Server created new checkpoint {} after initial request - state advanced", 
                            currentCheckpoint);
            } else if (checkpointWhenSent != null && currentCheckpoint != null 
                       && !checkpointWhenSent.equals(currentCheckpoint)) {
                // Checkpoint changed - server processed something
                checkpointAdvanced = true;
                comparisonResult = "checkpoint_changed";
                logger.info("Checkpoint changed from {} to {} - server state advanced, skipping retry",
                            checkpointWhenSent, currentCheckpoint);
            } else if (checkpointWhenSent != null && checkpointWhenSent.equals(currentCheckpoint)) {
                // Checkpoint unchanged - safe to retry
                comparisonResult = "checkpoint_unchanged";
                logger.info("Checkpoint unchanged ({}) - safe to retry in-flight message", currentCheckpoint);
            }

            // Record checkpoint comparison metric
            webSocketMetrics.recordCheckpointComparison(comparisonResult);

            if (checkpointAdvanced) {
                // Server has advanced - don't retry to avoid duplicates
                logger.info("Skipping retry for session {} - server state has advanced (checkpoint: {} -> {})",
                            sessionId, checkpointWhenSent, currentCheckpoint);
                webSocketMetrics.recordMessageRetrySkipped("checkpoint_advanced");
                clearInFlightState();
                return;
            }

            // Safe to retry - increment retry count
            messageRetryCount++;
            logger.info("Retrying in-flight message for session: {}, type: {}, toolId: {}, attempt: {}/{}",
                        sessionId, lastSentMessageType, lastSentToolId, messageRetryCount, MAX_MESSAGE_RETRIES);
            
            retryInFlightMessage(sessionId);
        }
    }

    /**
     * Clears the in-flight message tracking state.
     */
    private void clearInFlightState() {
        this.messageInFlight = false;
        this.lastSentPayload = null;
        this.lastSentMessageType = null;
        this.lastSentToolId = null;
        this.checkpointWhenSent = null;
        this.messageRetryCount = 0;
    }

    /**
     * Retries the in-flight message after reconnection.
     * Should only be called after checkpoint validation confirms retry is safe.
     *
     * @param sessionId The session ID to send the retry to
     */
    private void retryInFlightMessage(String sessionId) {
        if (lastSentPayload == null) {
            logger.warn("No payload to retry for session: {}", sessionId);
            clearInFlightState();
            return;
        }

        try {
            if ("IDERetrievalAnswer".equals(lastSentMessageType)) {
                logger.info("Retrying tool response for session: {}, toolId: {}, attempt: {}/{}", 
                            sessionId, lastSentToolId, messageRetryCount, MAX_MESSAGE_RETRIES);
                
                // Reset ready signal before retry (we'll wait for next READY)
                resetReadySignal("retrying tool response after reconnection");
                
                // Update checkpoint tracking for the retry
                this.checkpointWhenSent = this.lastKnownCheckpoint;
                
                // Re-send the tool response
                webSocketService.sendObject(WireMsgRouteKey.IDERetrievalAnswer, sessionId, lastSentPayload);
                
                // Record successful retry attempt
                webSocketMetrics.recordMessageRetryAttempt("IDERetrievalAnswer");
                
                logger.info("Successfully re-sent tool response for session: {}", sessionId);
                
            } else if ("UserQuery".equals(lastSentMessageType)) {
                logger.info("Retrying user query for session: {}, attempt: {}/{}", 
                            sessionId, messageRetryCount, MAX_MESSAGE_RETRIES);
                
                // Reset ready signal before retry
                resetReadySignal("retrying user query after reconnection");
                
                // Update checkpoint tracking for the retry
                this.checkpointWhenSent = this.lastKnownCheckpoint;
                
                // Re-send the user query
                webSocketService.sendObject(WireMsgRouteKey.UserQuery, sessionId, lastSentPayload);
                
                // Record successful retry attempt
                webSocketMetrics.recordMessageRetryAttempt("UserQuery");
                
                logger.info("Successfully re-sent user query for session: {}", sessionId);
                
            } else {
                logger.warn("Unknown message type for retry: {} - clearing in-flight state", lastSentMessageType);
                clearInFlightState();
            }
            
        } catch (Exception e) {
            logger.error("Failed to retry in-flight message for session: {} - {}", sessionId, e.getMessage(), e);
            
            // Record retry failure
            webSocketMetrics.recordMessageRetryFailure(lastSentMessageType);
            
            clearInFlightState();
            
            // Complete ready signal exceptionally to propagate error
            completeReadySignalExceptionally(new CommandException("Failed to retry message: " + e.getMessage(), e));
        }
    }

    /**
     * Schedules a session timeout task that will terminate the session if it exceeds the configured duration.
     * This prevents sessions from running indefinitely and consuming resources.
     * 
     * <p>When the timeout is triggered, this method performs several cleanup actions:
     * <ul>
     *   <li>Records a session timeout metric for monitoring</li>
     *   <li>Completes the ready signal exceptionally to propagate the error through the CompletableFuture chain</li>
     *   <li>Counts down the completion latch to unblock the waiting thread in process()</li>
     *   <li>Disconnects the WebSocket connection to free resources</li>
     * </ul>
     * 
     * <p>These additional actions beyond the plan ensure proper error propagation and resource cleanup,
     * preventing the session from hanging indefinitely and allowing the JMS transaction to complete appropriately.
     */
    private void scheduleSessionTimeout() {
        sessionStartTime = Instant.now();
        long timeoutSeconds = qodoProperties.getWebsocket().getTotalSessionTimeoutSeconds();
        
        sessionTimeoutTask = sessionScheduler.schedule(() -> {
            Duration sessionDuration = Duration.between(sessionStartTime, Instant.now());
            logger.error("Session timeout exceeded for session: {} - session has been running for {}s (limit: {}s)",
                         commandSession != null ? commandSession.sessionId() : "unknown",
                         sessionDuration.toSeconds(), timeoutSeconds);
            
            // Record session timeout metric
            webSocketMetrics.recordSessionTimeout();
            
            // Complete ready signal exceptionally to propagate timeout error
            // This ensures the error flows through the CompletableFuture chain to the process() method
            completeReadySignalExceptionally(new CommandException(
                String.format("Session timeout exceeded - session ran for %ds (limit: %ds)", 
                              sessionDuration.toSeconds(), timeoutSeconds)));
            
            // Count down completion latch to unblock waiting thread
            // This prevents the process() method from hanging indefinitely
            completionLatch.countDown();
            
            // Disconnect WebSocket if still connected
            // This ensures proper resource cleanup and prevents connection leaks
            if (commandSession != null) {
                try {
                    webSocketService.disconnectSession(commandSession.sessionId(), 1000, "Session timeout");
                } catch (Exception e) {
                    logger.warn("Error disconnecting session after timeout: {}", commandSession.sessionId(), e);
                }
            }
        }, timeoutSeconds, TimeUnit.SECONDS);
        
        logger.debug("Scheduled session timeout for session: {} in {}s", 
                     commandSession != null ? commandSession.sessionId() : "unknown", timeoutSeconds);
    }

    /**
     * Cancels the session timeout task if it's still pending.
     * Should be called when the session completes normally (ENDNODE received).
     */
    private void cancelSessionTimeout() {
        if (sessionTimeoutTask != null && !sessionTimeoutTask.isDone()) {
            boolean cancelled = sessionTimeoutTask.cancel(false);
            if (cancelled) {
                Duration sessionDuration = sessionStartTime != null ? 
                    Duration.between(sessionStartTime, Instant.now()) : Duration.ZERO;
                logger.debug("Cancelled session timeout for session: {} after {}s", 
                             commandSession != null ? commandSession.sessionId() : "unknown",
                             sessionDuration.toSeconds());
            }
        }
    }

}