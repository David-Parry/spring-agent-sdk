/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;


/**
 * Configuration for an AI assistant
 * @param version The version of the configuration
 * @param systemPrompt The system prompt for the assistant
 * @param instructions Instructions for the assistant
 * @param commands Map of command names to their configurations
 * @param imports List of imports
 * @param mcpServers MCP servers configuration
 * @param model The AI model to use
 * @param availableTools List of available tools
 * @param outputSchema The output schema
 * @param exitExpression Expression for exiting
 * @param executionStrategy The execution strategy
 */
public record AIAssistantConfig(
    @JsonProperty("version") String version,
    @JsonProperty("system_prompt") String systemPrompt,
    @JsonProperty("instructions") String instructions,
    @JsonProperty("commands") Map<String, CommandConfig> commands,
    @JsonProperty("imports") List<String> imports,
    @JsonProperty("mcpServers") Object mcpServers,
    @JsonProperty("model") String model,
    @JsonProperty("available_tools") List<String> availableTools,
    @JsonProperty("output_schema") Object outputSchema,
    @JsonProperty("exit_expression") String exitExpression,
    @JsonProperty("execution_strategy") String executionStrategy
) {}
