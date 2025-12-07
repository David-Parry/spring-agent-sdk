/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.mcp;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Custom deserializer for McpServer that determines the concrete type
 * based on the presence of specific fields in the JSON.
 */
public class McpServerDeserializer extends JsonDeserializer<McpServer> {

    @Override
    public McpServer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.readValueAsTree();

        // If the node has a "command" field, it's a CommandServer
        if (node.has("command")) {
            String command = node.get("command").asText();
            List<String> args = List.of();
            if (node.has("args") && node.get("args").isArray()) {
                args = new java.util.ArrayList<>();
                for (JsonNode argNode : node.get("args")) {
                    args.add(argNode.asText());
                }
            }
            Map<String, String> env = Map.of();
            if (node.has("env") && node.get("env").isObject()) {
                Map<String, String> envMap = new java.util.HashMap<>();
                node
                        .get("env")
                        .fields()
                        .forEachRemaining(entry -> envMap.put(entry.getKey(), entry.getValue().asText()));
                env = envMap;
            }
            return new CommandServer(command, args, env);
        } else if (isHttpServer(node)) {
            String type = node.has("type") ? node.get("type").asText() : null;
            String url = node.has("url") ? node.get("url").asText() : null;
            
            Map<String, String> headers = Map.of();
            if (node.has("headers") && node.get("headers").isObject()) {
                Map<String, String> headersMap = new java.util.HashMap<>();
                node
                        .get("headers")
                        .fields()
                        .forEachRemaining(entry -> headersMap.put(entry.getKey(), entry.getValue().asText()));
                headers = headersMap;
            }
            
            Map<String, String> env = Map.of();
            if (node.has("env") && node.get("env").isObject()) {
                Map<String, String> envMap = new java.util.HashMap<>();
                node
                        .get("env")
                        .fields()
                        .forEachRemaining(entry -> envMap.put(entry.getKey(), entry.getValue().asText()));
                env = envMap;
            }
            
            return new HttpServer(type, url, headers, env);
        }

        // Default fallback
        throw new IllegalArgumentException("Unable to determine MCP server type from JSON: " + node);
    }

    /**
     * Determines if a JsonNode represents an HTTP server configuration.
     * An HTTP server is identified by either:
     * - Having a "type" field with value "http" or "streamable-http"
     * - Having a "url" field (regardless of type)
     */
    private boolean isHttpServer(JsonNode node) {
        // Check if it has a URL field (sufficient condition)
        if (node.has("url")) {
            return true;
        }

        // Check if it has a type field with HTTP-related values
        if (node.has("type")) {
            String type = node.get("type").asText();
            return "http".equalsIgnoreCase(type) || "streamable-http".equalsIgnoreCase(type);
        }

        return false;
    }
}