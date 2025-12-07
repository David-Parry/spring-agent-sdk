/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TaskStartResponse(
    @JsonProperty(value = "session_id", required = true) String sessionId,
    @JsonProperty(value = "data", required = false) TaskResponseData data,
    @JsonProperty(value = "type", required = false) String type,
    @JsonProperty(value = "sub_type", required = false) String subType
) {}

