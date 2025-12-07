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
 * Represents an API request
 * @param method The HTTP method
 * @param url The URL to request
 * @param data The request data
 * @param requestId The request identifier
 * @param timeout The request timeout in milliseconds
 */
public record APIRequest(
    @JsonProperty("method") String method,
    @JsonProperty("url") String url,
    @JsonProperty("data") Object data,
    @JsonProperty("requestId") String requestId,
    @JsonProperty("timeout") Integer timeout
) {}
