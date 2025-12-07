/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.mcp;

import java.util.Map;
/**
 * Represents MCP servers associated with an agent command
 * @param commandName The name of the command
 * @param mcpServers Map of initialized MCP servers with tools
 * @param noToolsServers Map of MCP servers without tools
 */
public record AgentMcpServers(String commandName, Map<String, McpServerInitialized> mcpServers, Map<String, McpServer> noToolsServers) {
}
