/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Root configuration record for agent.yml file
 * @param version The version of the agent configuration
 * @param systemPrompt The system prompt for the agent
 * @param commands Map of command names to their configurations
 */
public record AgentConfig(String version, @JsonProperty("system_prompt") String systemPrompt,
                          Map<String, AgentCommand> commands) {
}