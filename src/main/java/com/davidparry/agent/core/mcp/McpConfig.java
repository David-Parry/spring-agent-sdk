/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Root configuration record for MCP (Model Context Protocol) configuration.
 * Represents the top-level structure of mcp.json file.
 * @param mcpServers Map of MCP server names to their configurations
 */
public record McpConfig(
    @JsonProperty("mcpServers")
    Map<String, McpServer> mcpServers
) {
}