/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TaskResponseData(@JsonProperty(value = "tool", required = false) String tool,
                               @JsonProperty(value = "type", required = false) String type,
                               @JsonProperty(value = "tool_args", required = false) ToolArgs toolArgs,
                               @JsonProperty(value = "identifier", required = false) String identifier,
                               @JsonProperty(value = "server_name", required = false) String serverName) {
}
