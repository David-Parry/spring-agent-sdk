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

public record CommandConfig(
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("instructions") String instructions,
    @JsonProperty("available_tools") List<String> availableTools,
    @JsonProperty("model") String model,
    @JsonProperty("arguments") List<CommandArgument> arguments,
    @JsonProperty("triggers") CommandTriggers triggers,
    @JsonProperty("mcpServers") Object mcpServers,
    @JsonProperty("output_schema") Object outputSchema,
    @JsonProperty("exit_expression") String exitExpression,
    @JsonProperty("execution_strategy") String executionStrategy,
    @JsonProperty("chatMessage") String chatMessage,
    @JsonProperty("permissions") String permissions
) {}
