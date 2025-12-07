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


/**
 * Top-level server response for WebSocket "task" messages.
 * Required fields mirror AgentAPI.isValidTaskResponse:
 *  - session_id: required
 *  - data: required (and must contain data.tool)
 *
 * Optional top-level fields (seen in other response shapes like error/history):
 *  - type, sub_type, error, message
 * @param sessionId The session identifier for the task
 * @param data The data payload containing tool information
 * @param type The type of the response
 * @param subType The sub-type of the response
 * @param error Error message if an error occurred
 * @param message Additional message information
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskResponse(
        @JsonProperty(value = "session_id", required = false) String sessionId,

        // The data payload is required for all valid task responses
        @JsonProperty(value = "data", required = false) ToolData data,

        // Optional fields used in other envelopes (e.g., errors, history)
        @JsonProperty(value = "type", required = false) String type,
        @JsonProperty(value = "sub_type", required = false) String subType,
        @JsonProperty(value = "error", required = false) String error,
        @JsonProperty(value = "message", required = false) String message
) {}
