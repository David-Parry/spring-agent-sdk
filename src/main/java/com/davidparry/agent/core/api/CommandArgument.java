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
 * Represents a command argument in the agent configuration
 * @param name The name of the argument
 * @param type The type of the argument
 * @param required Whether the argument is required
 * @param defaultValue The default value for the argument
 * @param description The description of the argument
 */
public record CommandArgument(
    String name,
    String type,
    boolean required,
    @JsonProperty("default") String defaultValue,
    String description
) {}