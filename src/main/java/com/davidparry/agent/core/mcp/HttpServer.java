/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Record representing an HTTP-based MCP server configuration.
 * This type of server is accessed via HTTP URL.
 * @param type The type of the server (typically "http")
 * @param url The URL of the HTTP server
 * @param headers Optional HTTP headers to include in requests
 * @param env Optional environment variables for the server
 */
public record HttpServer(
    @JsonProperty("type")
    String type,
    
    @JsonProperty("url")
    String url,
    
    @JsonProperty("headers")
    Map<String, String> headers,
    
    @JsonProperty("env")
    Map<String, String> env
) implements McpServer {
    
    /**
     * Constructor for HttpServer when only URL is provided (type field is optional).
     *
     * @param url the base URL of the HTTP server
     */
    public HttpServer(String url) {
        this("http", url, null, null);
    }
    
    /**
     * Constructor for HttpServer with URL and headers.
     *
     * @param url     the base URL of the HTTP server
     * @param headers optional HTTP headers to include with requests
     */
    public HttpServer(String url, Map<String, String> headers) {
        this("http", url, headers, null);
    }
    
    /**
     * Constructor for HttpServer with URL and environment variables.
     *
     * @param url     the base URL of the HTTP server
     * @param headers optional HTTP headers to include with requests
     * @param env     optional environment variables for the server
     */
    public HttpServer(String url, Map<String, String> headers, Map<String, String> env) {
        this("http", url, headers, env);
    }
}