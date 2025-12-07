/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder class for creating TaskBaseData objects.
 * Provides a fluent API for constructing TaskBaseData instances.
 */
public class TaskBaseDataBuilder {
    
    private List<String> projectsRootPath;
    private String projectStructure;
    private String instructions;
    private String systemPrompt;
    private String cwd;
    
    public TaskBaseDataBuilder() {
        this.projectsRootPath = new ArrayList<>();
    }
    
    public TaskBaseDataBuilder projectsRootPath(List<String> projectsRootPath) {
        this.projectsRootPath = new ArrayList<>(projectsRootPath);
        return this;
    }
    
    public TaskBaseDataBuilder addProjectRootPath(String path) {
        if (this.projectsRootPath == null) {
            this.projectsRootPath = new ArrayList<>();
        }
        this.projectsRootPath.add(path);
        return this;
    }
    
    public TaskBaseDataBuilder projectStructure(String projectStructure) {
        this.projectStructure = projectStructure;
        return this;
    }
    
    public TaskBaseDataBuilder instructions(String instructions) {
        this.instructions = instructions;
        return this;
    }
    
    public TaskBaseDataBuilder systemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }
    
    public TaskBaseDataBuilder cwd(String cwd) {
        this.cwd = cwd;
        return this;
    }
    
    /**
     * Builds the TaskBaseData from the configured parameters.
     * 
     * @return A new TaskBaseData instance
     */
    public TaskBaseData build() {
        return new TaskBaseData(
            projectsRootPath,
            projectStructure,
            instructions,
            systemPrompt,
            cwd
        );
    }
    

}