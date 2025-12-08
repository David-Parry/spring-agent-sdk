/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an agent session with its associated data and state
 * @param sessionId The session identifier
 * @param requestId The request identifier
 * @param abortController Controller for aborting the session
 * @param data The task request data
 * @param command The command configuration
 * @param userEngagementCallback Callback for user engagement
 * @param waitingForResponse Whether the session is waiting for a response
 * @param lastPacketTimestamp Timestamp of the last packet received
 */
public record AgentSession(
    @JsonProperty("sessionId") String sessionId,
    @JsonProperty("requestId") String requestId,
    @JsonProperty("abortController") Object abortController,
    @JsonProperty("data") TaskRequestData data,
    @JsonProperty("command") CommandConfig command,
    @JsonProperty("userEngagementCallback") Object userEngagementCallback,
    @JsonProperty("waitingForResponse") Boolean waitingForResponse,
    @JsonProperty("lastPacketTimestamp") Long lastPacketTimestamp
) {}
