/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.api;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * Represents a complete agent request combining base data, task base data, and task request data
 * @param baseData The base data for the request
 * @param taskBaseData The task-specific base data
 * @param taskRequestData The task request data
 */
public record AgentRequest(
    @JsonUnwrapped BaseData baseData,
    @JsonUnwrapped TaskBaseData taskBaseData,
    @JsonUnwrapped TaskRequestData taskRequestData
) {}
