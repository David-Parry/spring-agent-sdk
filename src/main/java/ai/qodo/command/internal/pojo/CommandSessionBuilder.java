/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.pojo;

import ai.qodo.command.internal.mcp.AgentCommand;
import com.fasterxml.jackson.databind.JsonNode;
import io.modelcontextprotocol.client.McpSyncClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builder class for creating CommandSession objects.
 * Provides a fluent API for constructing CommandSession instances with proper validation.
 * 
 * <p>Example usage:
 * <pre>
 * CommandSession session = new CommandSessionBuilder()
 *     .messageType("jira-webhook")
 *     .sessionId("session-123")
 *     .requestId("request-456")
 *     .agentCommand(command)
 *     .eventKey("event-789")
 *     .mcpClients(clients)
 *     .build();
 * </pre>
 * 
 * <p>Or using the static factory method:
 * <pre>
 * CommandSession session = CommandSessionBuilder.withSession("jira-webhook", "session-123", "request-456")
 *     .agentCommand(command)
 *     .eventKey("event-789")
 *     .mcpClients(clients)
 *     .build();
 * </pre>
 */
public class CommandSessionBuilder {
    
    private String messageType;
    private String sessionId;
    private AgentCommand agentCommand;
    private String eventKey;
    private String requestId;
    private Map<String, McpSyncClient> mcpClients;
    private Instant createdAt;
    private int attemptCount;
    private JsonNode payload;
    private String checkPointId;
    private String projectStringStructure;
    
    /**
     * Creates a new CommandSessionBuilder with default values.
     * The mcpClients map is initialized to an empty HashMap.
     */
    public CommandSessionBuilder() {
        this.mcpClients = new HashMap<>();
    }
    
    /**
     * Sets the message type for the command session.
     * 
     * @param messageType the message type (required)
     * @return this builder instance for method chaining
     */
    public CommandSessionBuilder messageType(String messageType) {
        this.messageType = messageType;
        return this;
    }
    
    /**
     * Sets the session ID for the command session.
     * 
     * @param sessionId the session ID (required)
     * @return this builder instance for method chaining
     */
    public CommandSessionBuilder sessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }
    
    /**
     * Sets the agent command for the command session.
     * 
     * @param agentCommand the agent command (optional, may be null)
     * @return this builder instance for method chaining
     */
    public CommandSessionBuilder agentCommand(AgentCommand agentCommand) {
        this.agentCommand = agentCommand;
        return this;
    }
    
    /**
     * Sets the event key for the command session.
     * 
     * @param eventKey the event key (optional)
     * @return this builder instance for method chaining
     */
    public CommandSessionBuilder eventKey(String eventKey) {
        this.eventKey = eventKey;
        return this;
    }
    
    /**
     * Sets the request ID for the command session.
     * 
     * @param requestId the request ID (required)
     * @return this builder instance for method chaining
     */
    public CommandSessionBuilder requestId(String requestId) {
        this.requestId = requestId;
        return this;
    }
    
    /**
     * Sets the MCP clients map for the command session.
     * 
     * @param mcpClients the map of MCP clients (optional, defaults to empty map if null)
     * @return this builder instance for method chaining
     */
    public CommandSessionBuilder mcpClients(Map<String, McpSyncClient> mcpClients) {
        this.mcpClients = mcpClients;
        return this;
    }
    
    /**
     * Sets the creation timestamp for the command session.
     * 
     * @param createdAt the creation timestamp (optional, defaults to current time if null)
     * @return this builder instance for method chaining
     */
    public CommandSessionBuilder createdAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }
    
    /**
     * Sets the attempt count for the command session.
     * 
     * @param attemptCount the attempt count (defaults to 0 if not set)
     * @return this builder instance for method chaining
     */
    public CommandSessionBuilder attemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
        return this;
    }
    
    /**
     * Sets the payload for the command session.
     * 
     * @param payload the JSON payload (optional, may be null)
     * @return this builder instance for method chaining
     */
    public CommandSessionBuilder payload(JsonNode payload) {
        this.payload = payload;
        return this;
    }
    
    /**
     * Sets the checkpoint ID for the command session.
     * 
     * @param checkPointId the checkpoint ID (optional, may be null)
     * @return this builder instance for method chaining
     */
    public CommandSessionBuilder checkPointId(String checkPointId) {
        this.checkPointId = checkPointId;
        return this;
    }
    
    /**
     * Sets the project string structure for the command session.
     * 
     * @param projectStringStructure the project string structure (optional, may be null)
     * @return this builder instance for method chaining
     */
    public CommandSessionBuilder projectStringStructure(String projectStringStructure) {
        this.projectStringStructure = projectStringStructure;
        return this;
    }
    
    /**
     * Adds a single MCP client to the clients map.
     * This is a convenience method for incrementally building the clients map.
     * 
     * @param name the name/key for the client
     * @param client the MCP client instance
     * @return this builder instance for method chaining
     */
    public CommandSessionBuilder addMcpClient(String name, McpSyncClient client) {
        if (this.mcpClients == null) {
            this.mcpClients = new HashMap<>();
        }
        this.mcpClients.put(name, client);
        return this;
    }
    
    /**
     * Builds and returns a new CommandSession instance.
     * Validates that all required fields are set before construction.
     * 
     * @return a new CommandSession instance
     * @throws IllegalStateException if any required field is null or empty
     */
    public CommandSession build() {
        // Validate required fields
        if (messageType == null || messageType.trim().isEmpty()) {
            throw new IllegalStateException("messageType is required and cannot be null or empty");
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalStateException("sessionId is required and cannot be null or empty");
        }
        if (requestId == null || requestId.trim().isEmpty()) {
            throw new IllegalStateException("requestId is required and cannot be null or empty");
        }
        
        // Use defaults for optional fields
        Map<String, McpSyncClient> clients = (mcpClients != null) ? mcpClients : new HashMap<>();
        Instant timestamp = (createdAt != null) ? createdAt : Instant.now();
        
        return new CommandSession(messageType, sessionId, agentCommand, eventKey, requestId, clients, timestamp, attemptCount, payload, checkPointId, projectStringStructure);
    }
    
    /**
     * Static factory method to create a new builder instance.
     * 
     * @return a new CommandSessionBuilder
     */
    public static CommandSessionBuilder create() {
        return new CommandSessionBuilder();
    }
    
    /**
     * Static factory method to create a builder with basic session information pre-populated.
     * This is a convenience method for the common case where you have the core session data upfront.
     * 
     * @param messageType the message type (required)
     * @param sessionId the session ID (required)
     * @param requestId the request ID (required)
     * @return a new CommandSessionBuilder with the specified fields set
     */
    public static CommandSessionBuilder withSession(String messageType, String sessionId, String requestId) {
        return new CommandSessionBuilder()
            .messageType(messageType)
            .sessionId(sessionId)
            .requestId(requestId);
    }

    public static CommandSessionBuilder requestSession(String sessionId, String requestId, String checkPointId) {
        return new CommandSessionBuilder()
                .sessionId(sessionId)
                .requestId(requestId)
                .checkPointId(checkPointId);
    }


    /**
     * Static factory method to create a builder from an existing CommandSession.
     * Copies all fields from the provided session and increments the attemptCount by 1.
     * This is useful for retry scenarios where you want to create a new session based on an existing one.
     * 
     * <p>Example usage:
     * <pre>
     * CommandSession retrySession = CommandSessionBuilder.fromSession(originalSession).build();
     * // retrySession will have attemptCount = originalSession.attemptCount() + 1
     * </pre>
     * 
     * @param session the existing CommandSession to copy from
     * @return a new CommandSessionBuilder with all fields copied and attemptCount incremented
     * @throws IllegalArgumentException if session is null
     */
    public static CommandSessionBuilder fromSession(CommandSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session cannot be null");
        }
        
        return new CommandSessionBuilder()
            .messageType(session.messageType())
            .sessionId(session.sessionId())
            .agentCommand(session.agentCommand())
            .eventKey(session.eventKey())
            .requestId(session.requestId())
            .mcpClients(session.mcpClients())
            .createdAt(session.createdAt())
            .attemptCount(session.attemptCount() + 1)
            .payload(session.payload())
            .checkPointId(session.checkPointId())
            .projectStringStructure(session.projectStringStructure());
    }

    /**
     * Static factory method to create a builder from an existing CommandSession with an updated checkpoint ID.
     * Copies all fields from the provided session WITHOUT incrementing attemptCount.
     * This is specifically for updating the checkpoint ID when receiving READY events.
     * 
     * <p>Example usage:
     * <pre>
     * CommandSession updatedSession = CommandSessionBuilder.withUpdatedCheckpoint(originalSession, newCheckpointId).build();
     * // updatedSession will have the new checkpoint but same attemptCount
     * </pre>
     * 
     * @param session the existing CommandSession to copy from
     * @param newCheckpointId the new checkpoint ID to set
     * @return a new CommandSessionBuilder with all fields copied and checkpoint updated
     * @throws IllegalArgumentException if session is null
     */
    public static CommandSessionBuilder withUpdatedCheckpoint(CommandSession session, String newCheckpointId) {
        if (session == null) {
            throw new IllegalArgumentException("session cannot be null");
        }
        
        return new CommandSessionBuilder()
            .messageType(session.messageType())
            .sessionId(session.sessionId())
            .agentCommand(session.agentCommand())
            .eventKey(session.eventKey())
            .requestId(session.requestId())
            .mcpClients(session.mcpClients())
            .createdAt(session.createdAt())
            .attemptCount(session.attemptCount())  // Keep original attempt count
            .payload(session.payload())
            .checkPointId(newCheckpointId)  // Update with new checkpoint
            .projectStringStructure(session.projectStringStructure());
    }

    /**
     * Static factory method to create a builder from an existing CommandSession with a new request ID.
     * Used for reconnection scenarios where a fresh request ID is required for each new connection attempt.
     * Copies all fields from the provided session, generates a new request ID, and increments attemptCount.
     * 
     * <p>This ensures that each reconnection attempt has a unique request ID while maintaining
     * session continuity (same sessionId) and checkpoint-based state resumption (same checkPointId).
     * 
     * <p>Example usage:
     * <pre>
     * CommandSession reconnectSession = CommandSessionBuilder.fromSessionWithNewRequestId(originalSession).build();
     * // reconnectSession will have:
     * // - Same sessionId (for session continuity)
     * // - New requestId (unique for this connection attempt)
     * // - Same checkPointId (for state resumption)
     * // - attemptCount = originalSession.attemptCount() + 1
     * </pre>
     * 
     * @param session the existing CommandSession to copy from
     * @return a new CommandSessionBuilder with all fields copied, new request ID, and attemptCount incremented
     * @throws IllegalArgumentException if session is null
     */
    public static CommandSessionBuilder fromSessionWithNewRequestId(CommandSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session cannot be null");
        }
        
        // Generate a new request ID for this reconnection attempt
        String newRequestId = UUID.randomUUID().toString();
        
        return new CommandSessionBuilder()
            .messageType(session.messageType())
            .sessionId(session.sessionId())  // Keep same session ID for continuity
            .agentCommand(session.agentCommand())
            .eventKey(session.eventKey())
            .requestId(newRequestId)  // New request ID for this connection
            .mcpClients(session.mcpClients())
            .createdAt(session.createdAt())
            .attemptCount(session.attemptCount() + 1)  // Increment attempt count
            .payload(session.payload())
            .checkPointId(session.checkPointId())  // Preserve checkpoint for state resumption
            .projectStringStructure(session.projectStringStructure());
    }
}
