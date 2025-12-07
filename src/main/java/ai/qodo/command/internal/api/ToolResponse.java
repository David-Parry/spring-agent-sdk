/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolResponse(
    @JsonProperty("agent_type") String agentType,
    @JsonProperty("session_id") String sessionId,
    @JsonProperty("user_data") UserDataRequest userData,
    @JsonProperty("git_sha1") String gitSha1,
    @JsonProperty("tools") Map<String,  List<McpSchema.Tool>> tools,
    @JsonProperty("permissions") String permissions,
    @JsonProperty(value = "projects_root_path", required = false) List<String> projectsRootPath,
    @JsonProperty("cwd") String cwd,
    @JsonProperty("tool") String tool,
    @JsonProperty("tool_id") String toolId,
    @JsonProperty("answer") ToolAnswer answer
) {}