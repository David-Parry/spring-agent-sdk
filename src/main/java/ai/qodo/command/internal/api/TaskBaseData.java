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

public record TaskBaseData(
    @JsonProperty("projects_root_path") List<String> projectsRootPath,
    @JsonProperty("project_structure") String projectStructure,
    @JsonProperty("instructions") String instructions,
    @JsonProperty("system_prompt") String systemPrompt,
    @JsonProperty("cwd") String cwd
) {}
