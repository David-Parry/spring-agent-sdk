/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.mcp;

import com.davidparry.agent.core.api.CommandArgument;
import com.davidparry.agent.core.api.OutputSchema;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a command configuration in the agent.yml file
 * @param description The description of the command
 * @param instructions The instructions for executing the command
 * @param model The AI model to use for this command
 * @param arguments The list of command arguments
 * @param mcpServers The MCP servers configuration string
 * @param tools The list of tools available for this command
 * @param executionStrategy The execution strategy for the command
 * @param outputSchemaString The output schema as a string
 * @param exitExpression The exit expression for the command
 * @param outputSchema The parsed output schema object
 * @param systemPrompt The system prompt for the command
 * @param version The version of the command
 * @param name The name of the command
 * @param mcpConfig The MCP configuration object
 * @param next The next command to execute in the workflow
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentCommand(
    String description,
    String instructions,
    String model,
    List<CommandArgument> arguments,
    @JsonProperty("mcpServers") String mcpServers,
    List<String> tools,
    @JsonProperty("execution_strategy") String executionStrategy,
    @JsonProperty("output_schema") String outputSchemaString,
    @JsonProperty("exit_expression") String exitExpression,
    OutputSchema outputSchema,
    String systemPrompt,
    String version,
    String name,
    McpConfig mcpConfig,
    @JsonProperty(value = "next", required = false) String next
) {
    /**
     * Constructor overload that takes an AgentCommand and an OutputSchema and copies all their fields
     * @param agentCommand The source agent command to copy from
     * @param outputSchema The output schema to use
     * @param systemPrompt The system prompt to use
     * @param version The version to use
     * @param name The name to use
     * @param mcpConfig The MCP configuration to use
     */
    public AgentCommand(AgentCommand agentCommand, OutputSchema outputSchema, String systemPrompt, String version, String name, McpConfig mcpConfig) {
        this(
            agentCommand.description(),
            agentCommand.instructions(),
            agentCommand.model(),
            agentCommand.arguments(),
            agentCommand.mcpServers(),
            agentCommand.tools(),
            agentCommand.executionStrategy(),
            agentCommand.outputSchemaString(),
            agentCommand.exitExpression(),
            outputSchema, systemPrompt, version, name, mcpConfig,
            agentCommand.next()
        );
    }
}