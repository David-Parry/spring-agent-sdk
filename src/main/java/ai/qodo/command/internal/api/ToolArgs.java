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

public record ToolArgs(
    @JsonProperty(value = "command", required = false) String command,
    @JsonProperty(value = "use_shell", required = false) Boolean useShell,
    @JsonProperty(value = "content", required = false) String content,
    @JsonProperty(value = "cwd",required = false) String cwd,
    @JsonProperty(value = "args",required = false) String args
) {}
