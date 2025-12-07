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
import java.util.Map;
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolData(
    @JsonProperty(value = "server_name",required = false) String serverName,
    @JsonProperty(value="tool",required = true) String tool,
    @JsonProperty(value = "tool_args",required = false) Map<String, Object> toolArgs,
    @JsonProperty(value="tool_reasoning", required = false) String toolReasoning,
    @JsonProperty(value= "identifier" ,required = false) String identifier,
    @JsonProperty(value= "pending_approval",required = false) Boolean pendingApproval,
    @JsonProperty(value = "tool_result",required = false) ToolResult toolResult,
    @JsonProperty(value = "tool_args_for_ui",required = false) Object toolArgsForUi,
    @JsonProperty(value = "session_id",required = false) String sessionId,
    @JsonProperty(value = "checkpoint_id", required = false) String checkpointId
) {}
