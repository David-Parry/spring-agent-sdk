/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal;

import ai.qodo.command.internal.api.StringConstants;
import ai.qodo.command.internal.config.MCPClientInitializer;
import ai.qodo.command.internal.mcp.AgentCommand;
import ai.qodo.command.internal.mcp.EnvSubstitution;
import ai.qodo.command.internal.mcp.McpConfig;
import ai.qodo.command.internal.metrics.AgentConfigMetrics;
import ai.qodo.command.internal.pojo.CommandSession;
import ai.qodo.command.internal.pojo.CommandSessionBuilder;
import ai.qodo.command.internal.pojo.WebSocketIds;
import ai.qodo.command.internal.service.*;
import ai.qodo.command.internal.util.WebSocketUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

import static ai.qodo.command.internal.service.MessageService.INCOMPLETE_NODE;

/**
 * Service responsible for routing messages to appropriate services based on the message type.
 * Looks up services by appending "-service" to the type field value from the message.
 */
@Service("messageEventRouter")
@Scope("prototype")
public class MessageEventRouter implements MessageRouter {
    private static final Logger logger = LoggerFactory.getLogger(MessageEventRouter.class);
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    private final MCPClientInitializer mcpClientInitializer;
    private final AgentConfigManager agentConfigManager;
    private final AgentConfigMetrics agentConfigMetrics;

    @Autowired
    public MessageEventRouter(ApplicationContext applicationContext, ObjectMapper objectMapper,
                              AgentConfigManager agentConfigManager, MCPClientInitializer mcpClientInitializer,
                              AgentConfigMetrics agentConfigMetrics) {
        this.applicationContext = applicationContext;
        this.objectMapper = objectMapper;
        this.agentConfigManager = agentConfigManager;
        this.mcpClientInitializer = mcpClientInitializer;
        this.agentConfigMetrics = agentConfigMetrics;
    }

    @Override
    public void processMessage(String message) {
        CommandSession commandSession = null;
        // should always be able to have a service name but if one can not be created then go to the incomplete
        String serviceName = INCOMPLETE_NODE;
        try {
            logger.debug("Processing message: ||\n {} ||\n", message);

            // Parse the message to extract the type field
            JsonNode messageNode = objectMapper.readTree(message);
            JsonNode typeNode = messageNode.get(MessagePublisher.MSG_TYPE);
            if (typeNode == null || typeNode.isNull()) {
                logger.warn("Message does not contain a '{}' field, skipping: {}", MessagePublisher.MSG_TYPE, message);
                return;
            }
            String messageType = typeNode.asText();
            if (messageType.isEmpty()) {
                logger.warn("Message contains empty '{}' field, skipping: {}", MessagePublisher.MSG_TYPE, message);
                return;
            }
            if (isPingEvent(messageNode)) {
                logger.debug("Skipping WebSocket notification for ping event");
                return;
            }

            String eventKey = extractEventKey(messageNode);
            WebSocketIds webSocketIds = WebSocketUtil.generateNewWebSocketIds();

            // Extract project structure if present in the message
            String projectStructure = null;
            JsonNode projectStructureNode = messageNode.get(StringConstants.PROJECT_STRUCTURE.getValue());
            if (projectStructureNode != null && !projectStructureNode.isNull()) {
                projectStructure = projectStructureNode.asText();
                logger.trace("Extracted project structure from message for session: {}", webSocketIds.sessionId());
            }

            CommandSessionBuilder commandSessionBuilder = new CommandSessionBuilder()
                    .messageType(messageType)
                    .sessionId(webSocketIds.sessionId())
                    .eventKey(eventKey)
                    .requestId(webSocketIds.requestId())
                    .payload(messageNode)
                    .checkPointId(webSocketIds.checkpointId())
                    .projectStringStructure(projectStructure);
            AgentCommand command = agentConfigManager.getAgentConfig().commands().get(messageType);
            if (command != null) {
                // Record successful lookup
                agentConfigMetrics.recordSuccessfulLookup(messageType);

                McpConfig substitutedMcpConfig = EnvSubstitution.substitute(command.mcpConfig());
                Map<String, McpSyncClient> clients = mcpClientInitializer.loadSyncClients(substitutedMcpConfig,
                                                                                          getSessionAbsolutePath(webSocketIds.sessionId()));
                commandSessionBuilder.agentCommand(command).mcpClients(clients);
            } else {
                // Record missing command metric
                agentConfigMetrics.recordMissingCommand(messageType);

                // Log warning for missing agent command
                logger.warn("No agent command configured for message type '{}'. Session: {}, EventKey: {}",
                            messageType, webSocketIds.sessionId(), eventKey);

                // Only allow EndFlowCleanup to proceed without agent command
                if (!messageType.equalsIgnoreCase(EndFlowCleanup.TYPE)) {
                    logger.error("Message type '{}' requires an agent command configuration but none was found. " +
                                         "Please add this message type to the agent configuration file.", messageType);
                    throw new MissingAgentCommandException(String.format("No agent command configured for message " + "type '%s'. " + "Available commands:" + " %s", messageType, agentConfigManager
                            .getAgentConfig()
                            .commands()
                            .keySet()));
                }
            }
            commandSession = commandSessionBuilder.build();
            if (messageType.equalsIgnoreCase(EndFlowCleanup.TYPE)) {
                serviceName = messageType + MessageService.SERVICE_SUFFIX;
            } else {
                serviceName = "websocketNotificationService";
            }
            logger.info("Routed to '{}'", serviceName);
            if (logger.isDebugEnabled()) {
                logger.debug("Service name: {} is being invoked with AgentCommand {}", serviceName, commandSession
                        .agentCommand()
                        .name());
            } else if (logger.isTraceEnabled()) {
                logger.debug("Service name: {} is being invoked with CommandSession {}", serviceName, commandSession);
            }
            MessageService service = applicationContext.getBean(serviceName, MessageService.class);
            service.init(commandSession);
            service.process();
            logger.info("Successfully processed routed messageType '{}' to service with service key '{}'",
                        messageType, service.serviceKey());
        } catch (BeansException be) {
            logger.error("Service not present if '{}' == '{}' this means the LLM did not return with complete " +
                                 "messages or it gave up. You can implement a service to handle this case for the service {} and even use the LLM to continue or recover", serviceName, INCOMPLETE_NODE, INCOMPLETE_NODE);
        } catch (Exception e) {
            logger.error("Failed to process message: {}", message, e);
            throw new CommandException("Failed to process message: " + message, e);
        } finally {
            closeSession(commandSession);
        }
    }

    protected void closeSession(CommandSession session) {
        if (session != null) {
            // Close MCP clients
            if (session.mcpClients() != null && !session.mcpClients().isEmpty()) {
                session.mcpClients().values().forEach(McpSyncClient::close);
            }
            // Clean up session directory
            cleanupSessionDirectory(session.sessionId());
        }
    }

    /**
     * Cleans up the session directory created for MCP tools.
     * This directory is created in the user's home directory and should be removed
     * after the session completes or fails.
     *
     * @param sessionId The session ID whose directory should be cleaned up
     */
    private void cleanupSessionDirectory(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            logger.warn("Cannot cleanup session directory - sessionId is null or empty");
            return;
        }

        try {
            String userHome = System.getProperty("user.home");
            Path sessionPath = Paths.get(userHome, sessionId);

            if (Files.exists(sessionPath)) {
                logger.info("Cleaning up session directory: {}", sessionPath);
                deleteDirectoryRecursively(sessionPath);
                logger.info("Successfully cleaned up session directory: {}", sessionPath);
            } else {
                logger.debug("Session directory does not exist, no cleanup needed: {}", sessionPath);
            }
        } catch (Exception e) {
            logger.error("Failed to cleanup session directory for sessionId: {}", sessionId, e);
            // Don't throw - cleanup failure shouldn't break the flow
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     *
     * @param path The path to delete
     * @throws IOException if deletion fails
     */
    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        if (Files.isDirectory(path)) {
            // Delete contents first
            try (var stream = Files.list(path)) {
                stream.forEach(child -> {
                    try {
                        deleteDirectoryRecursively(child);
                    } catch (IOException e) {
                        logger.warn("Failed to delete: {}", child, e);
                    }
                });
            }
        }

        // Delete the file or empty directory
        try {
            Files.delete(path);
            logger.debug("Deleted: {}", path);
        } catch (IOException e) {
            logger.warn("Failed to delete path: {}", path, e);
            throw e;
        }
    }

    private boolean isPingEvent(JsonNode payload) {
        return "ping".equalsIgnoreCase(payload.asText("eventType"));
    }

    private String getSessionAbsolutePath(String sessionId) {
        try {
            // Construct the path
            String userHome = System.getProperty("user.home");
            Path sessionPath = Paths.get(userHome, sessionId);

            // Check if directory exists, create if it doesn't
            if (!Files.exists(sessionPath)) {
                logger.debug("Session directory does not exist, creating: {}", sessionPath);
                Files.createDirectories(sessionPath);
            }

            // Verify the directory is writable
            if (!Files.isWritable(sessionPath)) {
                logger.error("Session directory is not writable: {}", sessionPath);
                throw new IOException("Session directory is not writable: " + sessionPath);
            }

            // Return the absolute path with trailing separator
            return sessionPath.toAbsolutePath() + File.separator;

        } catch (IOException e) {
            logger.error("Failed to create or verify session directory for sessionId: {}", sessionId, e);
            throw new RuntimeException("Failed to create session directory", e);
        }
    }

    private String extractEventKey(JsonNode payload) {
        if (payload.get("EventKey") != null && !payload.get("EventKey").asText().isEmpty()) {
            return payload.get("EventKey").asText();
        }
        return UUID.randomUUID().toString();
    }


}