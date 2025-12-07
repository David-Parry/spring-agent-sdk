/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.mcp;

import java.io.IOException;
import java.util.Map;

/**
 * Implementation of ConfigInitializer for MCP configurations.
 * This class reads MCP configurations and performs environment variable substitution.
 */
public class McpConfigInitializer implements ConfigInitializer<McpConfig> {
    
    private final ConfigReader<McpConfig> configReader;
    
    /**
     * Creates a new McpConfigInitializer with the given config reader.
     * 
     * @param configReader the underlying config reader to use
     */
    public McpConfigInitializer(ConfigReader<McpConfig> configReader) {
        this.configReader = configReader;
    }
    
    @Override
    public McpConfig init(ConfigSource source) throws IOException {
        // Step 1: Read the configuration using the underlying reader
        McpConfig rawConfig = configReader.readConfig(source);
        
        // Step 2: Perform environment variable substitution
        McpConfig substitutedConfig = EnvSubstitution.substitute(rawConfig);
        
        // Step 3: Validate the configuration (could be extended in the future)
        validateConfig(substitutedConfig);
        
        return substitutedConfig;
    }
    
    /**
     * Validates the configuration after substitution.
     * This method can be extended to perform additional validation.
     * 
     * @param config the configuration to validate
     * @throws IOException if the configuration is invalid
     */
    private void validateConfig(McpConfig config) throws IOException {
        if (config == null) {
            throw new IOException("Configuration is null");
        }
        
        if (config.mcpServers() == null) {
            throw new IOException("No MCP servers configured");
        }
        
        // Validate each server
        for (Map.Entry<String, McpServer> entry : config.mcpServers().entrySet()) {
            String serverName = entry.getKey();
            McpServer server = entry.getValue();
            
            if (server == null) {
                throw new IOException("Server '" + serverName + "' is null");
            }
            
            validateServer(serverName, server);
        }
    }
    
    /**
     * Validates a single MCP server configuration.
     * 
     * @param serverName the name of the server
     * @param server the server configuration
     * @throws IOException if the server configuration is invalid
     */
    private void validateServer(String serverName, McpServer server) throws IOException {
        if (server instanceof CommandServer commandServer) {
            if (commandServer.command() == null || commandServer.command().trim().isEmpty()) {
                throw new IOException("Server '" + serverName + "' has no command specified");
            }
        } else if (server instanceof HttpServer httpServer) {
            if (httpServer.url() == null || httpServer.url().trim().isEmpty()) {
                throw new IOException("Server '" + serverName + "' has no URL specified");
            }
        }
    }
}