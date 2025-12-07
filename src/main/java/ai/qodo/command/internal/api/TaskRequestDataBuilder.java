/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder class for creating TaskRequestData objects.
 * Provides a fluent API for constructing TaskRequestData instances.
 */
public class TaskRequestDataBuilder {
    
    private String userRequest;
    private Map<String, Object> userContext;
    private OutputSchema outputSchema;
    private String customModel;
    private Map<String, Object> answer;
    private String tool;
    private String toolId;
    private Boolean ciMode;
    private Integer maxIterations;
    private String executionStrategy;
    private String qodomd;
    private List<String> images;
    private String gitSha1;
    
    public TaskRequestDataBuilder() {
        this.userContext = new HashMap<>();
        this.images = new ArrayList<>();
    }
    
    public TaskRequestDataBuilder userRequest(String userRequest) {
        this.userRequest = userRequest;
        return this;
    }
    
    public TaskRequestDataBuilder userContext(Map<String, Object> userContext) {
        this.userContext = new HashMap<>(userContext);
        return this;
    }
    
    public TaskRequestDataBuilder addUserContext(String key, Object value) {
        if (this.userContext == null) {
            this.userContext = new HashMap<>();
        }
        this.userContext.put(key, value);
        return this;
    }
    
    public TaskRequestDataBuilder outputSchema(OutputSchema outputSchema) {
        this.outputSchema = outputSchema;
        return this;
    }

    public TaskRequestDataBuilder customModel(String customModel) {
        this.customModel = customModel;
        return this;
    }
    
    public TaskRequestDataBuilder answer(Map<String, Object> answer) {
        this.answer = answer;
        return this;
    }
    
    public TaskRequestDataBuilder tool(String tool) {
        this.tool = tool;
        return this;
    }
    
    public TaskRequestDataBuilder toolId(String toolId) {
        this.toolId = toolId;
        return this;
    }
    
    public TaskRequestDataBuilder ciMode(Boolean ciMode) {
        this.ciMode = ciMode;
        return this;
    }
    
    public TaskRequestDataBuilder maxIterations(Integer maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }
    
    public TaskRequestDataBuilder executionStrategy(String executionStrategy) {
        this.executionStrategy = executionStrategy;
        return this;
    }
    
    public TaskRequestDataBuilder qodomd(String qodomd) {
        this.qodomd = qodomd;
        return this;
    }
    
    public TaskRequestDataBuilder images(List<String> images) {
        this.images = new ArrayList<>(images);
        return this;
    }
    
    public TaskRequestDataBuilder addImage(String image) {
        if (this.images == null) {
            this.images = new ArrayList<>();
        }
        this.images.add(image);
        return this;
    }
    
    public TaskRequestDataBuilder gitSha1(String gitSha1) {
        this.gitSha1 = gitSha1;
        return this;
    }
    
    /**
     * Builds the TaskRequestData from the configured parameters.
     * 
     * @return A new TaskRequestData instance
     */
    public TaskRequestData build() {
        return new TaskRequestData(
            userRequest,
            userContext,
            outputSchema,
            customModel,
            answer,
            tool,
            toolId,
            ciMode,
            maxIterations,
            executionStrategy,
            qodomd,
            images,
            gitSha1
        );
    }
    
    /**
     * Static factory method to create a builder with default execution strategy.
     * 
     * @return A pre-configured TaskRequestDataBuilder
     */
    public static TaskRequestDataBuilder defaultExecution() {
        return new TaskRequestDataBuilder()
            .executionStrategy("act");
    }


}