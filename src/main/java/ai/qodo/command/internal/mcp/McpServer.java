/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.mcp;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Map;

/**
 * Base interface for MCP server configurations.
 * Uses custom deserializer to handle different server types based on field presence.
 */
@JsonDeserialize(using = McpServerDeserializer.class)
public sealed interface McpServer permits CommandServer, HttpServer {
    String STREAMABLE_HTTP_TYPE = "streamable-http";
    String HTTP_TYPE = "http";

    /**
     * Gets the environment variables for this MCP server.
     * Both CommandServer and HttpServer implementations provide this property.
     * 
     * @return a map of environment variable names to values, or null if not set
     */
    Map<String, String> env();
}

