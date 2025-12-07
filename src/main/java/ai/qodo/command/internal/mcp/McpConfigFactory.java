/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Factory class for creating MCP-specific configuration readers and initializers.
 * Follows the Factory pattern and provides a convenient API for MCP configurations.
 */
public final class McpConfigFactory {
    
    private McpConfigFactory() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Creates a default MCP configuration reader.
     * 
     * @return a ConfigReader specifically configured for McpConfig
     */
    public static ConfigReader<McpConfig> createReader() {
        return new JsonConfigReader<>(McpConfig.class);
    }
    
    /**
     * Creates an MCP configuration reader with a custom ObjectMapper.
     * 
     * @param objectMapper the ObjectMapper to use for JSON parsing
     * @return a ConfigReader specifically configured for McpConfig
     */
    public static ConfigReader<McpConfig> createReader(ObjectMapper objectMapper) {
        return new JsonConfigReader<>(objectMapper, McpConfig.class);
    }
    
    /**
     * Creates a default MCP configuration initializer.
     * This initializer reads the configuration and performs environment variable substitution.
     * 
     * @return a ConfigInitializer specifically configured for McpConfig
     */
    public static ConfigInitializer<McpConfig> createInitializer() {
        return new McpConfigInitializer(createReader());
    }
    
    /**
     * Creates an MCP configuration initializer with a custom ObjectMapper.
     * This initializer reads the configuration and performs environment variable substitution.
     * 
     * @param objectMapper the ObjectMapper to use for JSON parsing
     * @return a ConfigInitializer specifically configured for McpConfig
     */
    public static ConfigInitializer<McpConfig> createInitializer(ObjectMapper objectMapper) {
        return new McpConfigInitializer(createReader(objectMapper));
    }
}