/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.config.transport;

import ai.qodo.command.internal.mcp.McpServer;
import io.modelcontextprotocol.spec.McpClientTransport;

/**
 * Factory interface for creating MCP client transports based on server configuration.
 * Implementations should handle different transport types (STDIO, HTTP, Streamable HTTP).
 */
public interface TransportFactory {
    /**
     * Creates the appropriate transport based on the server configuration type.
     *
     * @param serverConfig the server configuration
     * @return the created transport, or null if the type is not supported
     */
    McpClientTransport createTransport(McpServer serverConfig);
}
