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

public record TaskRequestData(
    @JsonProperty("user_request") String userRequest,
    @JsonProperty("user_context") Map<String, Object> userContext,
    @JsonProperty("output_schema") OutputSchema outputSchema,
    @JsonProperty("custom_model") String customModel,
    @JsonProperty("answer") Map<String, Object> answer,
    @JsonProperty("tool") String tool,
    @JsonProperty("tool_id") String toolId,
    @JsonProperty("ci_mode") Boolean ciMode,
    @JsonProperty("max_iterations") Integer maxIterations,
    @JsonProperty("execution_strategy") String executionStrategy,
    @JsonProperty("qodomd") String qodomd,
    @JsonProperty("images") List<String> images,
    @JsonProperty("git_sha1") String gitSha1
) {}
