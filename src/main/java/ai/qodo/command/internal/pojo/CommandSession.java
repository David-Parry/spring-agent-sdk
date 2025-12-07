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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

public record CommandSession(String messageType, String sessionId, AgentCommand agentCommand, String eventKey,
                             String requestId, Map<String, McpSyncClient> mcpClients, Instant createdAt,
                             int attemptCount, JsonNode payload, String checkPointId, String projectStringStructure) {
    
    /**
     * Generates a WebSocket URL for connecting to the server.
     * 
     * @param wsBaseUrl the WebSocket base URL (e.g., from .env or QodoProperties)
     * @param isReconnect true if this is a reconnection attempt, false for initial connection
     * @return the complete WebSocket URL with appropriate query parameters
     */
    public String generateWebSocketUrl(String wsBaseUrl, boolean isReconnect) {
        if (wsBaseUrl == null || wsBaseUrl.isEmpty()) {
            throw new IllegalArgumentException("wsBaseUrl cannot be null or empty");
        }
        
        // Ensure base URL doesn't end with slash
        String base = wsBaseUrl.replaceAll("/+$", "");
        
        if (isReconnect && checkPointId != null && !checkPointId.isEmpty()) {
            // Reconnection URL with checkpoint_id
            return String.format("%s/v2/agentic/ws/connect?session_id=%s&request_id=%s&checkpoint_id=%s",
                base, 
                urlEncode(sessionId), 
                urlEncode(requestId),
                urlEncode(checkPointId));
        } else {
            // Initial connection URL
            return String.format("%s/v2/agentic/ws/connect?session_id=%s&request_id=%s",
                base, 
                urlEncode(sessionId), 
                urlEncode(requestId));
        }
    }
    
    /**
     * URL encodes a string for safe use in URLs.
     */
    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
