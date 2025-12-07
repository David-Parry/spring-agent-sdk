/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a response from an agent
 * @param content The content of the response
 * @param error Error message if an error occurred
 * @param forceStop Whether to force stop the agent
 * @param toolData Tool data associated with the response
 * @param sessionId The session identifier
 */
public record AgentResponse(
    @JsonProperty("content") String content,
    @JsonProperty("error") String error,
    @JsonProperty("forceStop") Boolean forceStop,
    @JsonProperty("toolData") ToolData toolData,
    @JsonProperty(value = "session_id", required = false) String sessionId

    ) {}
