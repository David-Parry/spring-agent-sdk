/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for authentication
 * @param token The authentication token
 * @param source The source of the authentication
 */
public record AuthConfig(
    @JsonProperty("token") String token,
    @JsonProperty("source") String source
) {}
