/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.api;

/**
 * Builder class for creating AgentRequest objects using sub-builders.
 * This builder provides a cleaner API by using dedicated builders for each data component.
 */
public class AgentRequestBuilder {

    private final BaseDataBuilder baseDataBuilder;
    private final TaskBaseDataBuilder taskBaseDataBuilder;
    private final TaskRequestDataBuilder taskRequestDataBuilder;

    /**
     * Constructs a new AgentRequestBuilder with initialized sub-builders
     */
    public AgentRequestBuilder() {
        this.baseDataBuilder = new BaseDataBuilder();
        this.taskBaseDataBuilder = new TaskBaseDataBuilder();
        this.taskRequestDataBuilder = new TaskRequestDataBuilder();
    }

    /**
     * Provides direct access to the base data builder
     * @return The base data builder
     */
    public BaseDataBuilder baseData() {
        return baseDataBuilder;
    }

    /**
     * Provides direct access to the task base data builder
     * @return The task base data builder
     */
    public TaskBaseDataBuilder taskBaseData() {
        return taskBaseDataBuilder;
    }

    /**
     * Provides direct access to the task request data builder
     * @return The task request data builder
     */
    public TaskRequestDataBuilder taskRequestData() {
        return taskRequestDataBuilder;
    }

    /**
     * Sets the session ID in the base data builder
     * @param sessionId The session identifier
     * @return This builder for method chaining
     */
    public AgentRequestBuilder sessionId(String sessionId) {
        baseDataBuilder.sessionId(sessionId);
        return this;
    }

    /**
     * Sets the tool in the task request data builder
     * @param tool The tool name
     * @return This builder for method chaining
     */
    public AgentRequestBuilder tool(String tool) {
        taskRequestDataBuilder.tool(tool);
        return this;
    }


    /**
     * Builds the AgentRequest from the configured sub-builders.
     *
     * @return A new AgentRequest instance
     */
    public AgentRequest build() {
        BaseData baseData = baseDataBuilder.build();
        TaskBaseData taskBaseData = taskBaseDataBuilder.build();
        TaskRequestData taskRequestData = taskRequestDataBuilder.build();

        return new AgentRequest(baseData, taskBaseData, taskRequestData);
    }


}