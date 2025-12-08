/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Record representing a command-based MCP server configuration.
 * This type of server is launched via a command with arguments.
 * @param command The command to execute to start the server
 * @param args The list of arguments to pass to the command
 * @param env Optional environment variables for the server process
 */
public record CommandServer(
    @JsonProperty("command")
    String command,
    
    @JsonProperty("args")
    List<String> args,
    
    @JsonProperty("env")
    Map<String, String> env
) implements McpServer {
}