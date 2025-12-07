/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.mcp;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utility class for working with MCP servers.
 * Demonstrates how the common env() method can be used
 * without knowing concrete server implementations.
 */
public final class McpServerUtils {
    
    private McpServerUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Gets an environment variable from any MCP server.
     * 
     * @param server the MCP server
     * @param variableName the environment variable name
     * @return the environment variable value, or empty if not found
     */
    public static Optional<String> getEnvVariable(McpServer server, String variableName) {
        Map<String, String> env = server.env();
        return env != null ? Optional.ofNullable(env.get(variableName)) : Optional.empty();
    }
    
    /**
     * Gets an environment variable with a default value.
     * 
     * @param server the MCP server
     * @param variableName the environment variable name
     * @param defaultValue the default value if not found
     * @return the environment variable value or the default value
     */
    public static String getEnvVariable(McpServer server, String variableName, String defaultValue) {
        return getEnvVariable(server, variableName).orElse(defaultValue);
    }
    
    /**
     * Checks if a server has any environment variables configured.
     * 
     * @param server the MCP server
     * @return true if the server has environment variables, false otherwise
     */
    public static boolean hasEnvironmentVariables(McpServer server) {
        Map<String, String> env = server.env();
        return env != null && !env.isEmpty();
    }
    
    /**
     * Gets the number of environment variables configured for a server.
     * 
     * @param server the MCP server
     * @return the number of environment variables
     */
    public static int getEnvVariableCount(McpServer server) {
        Map<String, String> env = server.env();
        return env != null ? env.size() : 0;
    }
    
    /**
     * Checks if a server has a specific environment variable.
     * 
     * @param server the MCP server
     * @param variableName the environment variable name
     * @return true if the variable exists, false otherwise
     */
    public static boolean hasEnvVariable(McpServer server, String variableName) {
        Map<String, String> env = server.env();
        return env != null && env.containsKey(variableName);
    }
    
    /**
     * Gets all environment variables as a formatted string.
     * Useful for logging or debugging.
     * 
     * @param server the MCP server
     * @return a formatted string of all environment variables
     */
    public static String formatEnvVariables(McpServer server) {
        Map<String, String> env = server.env();
        if (env == null || env.isEmpty()) {
            return "No environment variables";
        }
        
        return env.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining(", "));
    }
    
    /**
     * Creates a copy of the environment variables map.
     * Returns an empty map if no environment variables are set.
     * 
     * @param server the MCP server
     * @return a copy of the environment variables map
     */
    public static Map<String, String> copyEnvVariables(McpServer server) {
        Map<String, String> env = server.env();
        return env != null ? Map.copyOf(env) : Map.of();
    }
    
    /**
     * Filters environment variables by a prefix.
     * Useful for getting related configuration variables.
     * 
     * @param server the MCP server
     * @param prefix the prefix to filter by
     * @return a map of environment variables that start with the prefix
     */
    public static Map<String, String> getEnvVariablesWithPrefix(McpServer server, String prefix) {
        Map<String, String> env = server.env();
        if (env == null) {
            return Map.of();
        }
        
        return env.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(prefix))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}