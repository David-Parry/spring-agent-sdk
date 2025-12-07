/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.service;

import ai.qodo.command.internal.api.Handler;
import ai.qodo.command.internal.api.TaskResponse;
import ai.qodo.command.internal.pojo.CommandSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fallback handler for messages with unknown or unconfigured message types.
 * This handler logs detailed information about the unknown message to help with debugging
 * and configuration issues.
 */
@Service("unknownMessageTypeHandler")
public class UnknownMessageTypeHandler implements Handler {
    
    private static final Logger logger = LoggerFactory.getLogger(UnknownMessageTypeHandler.class);
    
    @Override
    public void handle(CommandSession session, List<TaskResponse> responses) {
        logger.error("=== Unknown Message Type Handler ===");
        logger.error("Received message with unknown or unconfigured message type");
        logger.error("Message Type: {}", session.messageType());
        logger.error("Session ID: {}", session.sessionId());
        logger.error("Event Key: {}", session.eventKey());
        logger.error("Request ID: {}", session.requestId());
        
        if (session.payload() != null) {
            logger.error("Payload: {}", session.payload().toPrettyString());
        } else {
            logger.error("Payload: null");
        }
        
        logger.error("Action Required:");
        logger.error("1. Add a configuration entry for message type '{}' in your agent configuration file", 
                    session.messageType());
        logger.error("2. Ensure the configuration includes:");
        logger.error("   - name: {}", session.messageType());
        logger.error("   - instructions: <template for processing this message type>");
        logger.error("   - systemPrompt: <system prompt for the agent>");
        logger.error("   - mcpServers: <optional MCP server configuration>");
        logger.error("   - tools: <optional list of tools>");
        logger.error("3. Restart the application to load the new configuration");
        logger.error("===================================");
    }
}
