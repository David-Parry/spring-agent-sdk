/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.mcp;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for performing environment variable substitution in configuration objects.
 * Replaces placeholders like {VAR_NAME} with actual values from environment variables.
 */
public final class EnvSubstitution {
    
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    
    private EnvSubstitution() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Performs environment variable substitution on an MCP configuration.
     * Uses System.getenv() to resolve placeholders in all string fields.
     * 
     * @param config the MCP configuration to process
     * @return a new configuration with environment variables substituted
     */
    public static McpConfig substitute(McpConfig config) {
        return substitute(config, System.getenv());
    }
    
    /**
     * Performs environment variable substitution on an MCP configuration.
     * Uses the provided environment map to resolve placeholders in all string fields.
     * This method is primarily for testing purposes.
     * 
     * @param config the MCP configuration to process
     * @param environmentVariables the environment variables to use for substitution
     * @return a new configuration with environment variables substituted
     */
    public static McpConfig substitute(McpConfig config, Map<String, String> environmentVariables) {
        if (config == null || config.mcpServers() == null) {
            return config;
        }
        
        Map<String, McpServer> substitutedServers = config.mcpServers().entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> substituteInServer(entry.getValue(), environmentVariables)
            ));
        
        return new McpConfig(substitutedServers);
    }
    
    /**
     * Performs environment variable substitution on a single MCP server.
     * Uses System.getenv() to resolve placeholders in all string fields.
     * 
     * @param server the server to process
     * @return a new server instance with environment variables substituted
     */
    private static McpServer substituteInServer(McpServer server) {
        return substituteInServer(server, System.getenv());
    }
    
    /**
     * Performs environment variable substitution on a single MCP server.
     * Uses the provided environment variables to resolve placeholders in all string fields.
     * 
     * @param server the server to process
     * @param environmentVariables the environment variables to use for substitution
     * @return a new server instance with environment variables substituted
     */
    private static McpServer substituteInServer(McpServer server, Map<String, String> environmentVariables) {
        if (server == null) {
            return null;
        }
        
        if (server instanceof CommandServer commandServer) {
            return substituteInCommandServer(commandServer, environmentVariables);
        } else if (server instanceof HttpServer httpServer) {
            return substituteInHttpServer(httpServer, environmentVariables);
        }
        
        return server;
    }
    
    /**
     * Performs environment variable substitution in a CommandServer.
     * Processes all string fields using system environment variables.
     */
    private static CommandServer substituteInCommandServer(CommandServer server, Map<String, String> systemEnv) {
        // First, substitute and resolve environment variables (including cross-references)
        Map<String, String> processedEnv = substituteInEnvironmentMap(server.env(), systemEnv);
        
        // Build a combined environment for field substitution: provided env + processed server env
        Map<String, String> combinedEnv = new java.util.HashMap<>(systemEnv != null ? systemEnv : java.util.Map.of());
        if (processedEnv != null) {
            combinedEnv.putAll(processedEnv);
        }
        
        // Substitute in command and args using combined environment
        String substitutedCommand = substituteString(server.command(), combinedEnv);
        
        java.util.List<String> substitutedArgs = server.args() != null 
            ? server.args().stream()
                .map(arg -> substituteString(arg, combinedEnv))
                .collect(java.util.stream.Collectors.toList())
            : server.args();


        return new CommandServer(substitutedCommand, substitutedArgs, processedEnv);
    }
    
    /**
     * Performs environment variable substitution in an HttpServer.
     * Processes all string fields using system environment variables.
     */
    private static HttpServer substituteInHttpServer(HttpServer server, Map<String, String> systemEnv) {
        // First, substitute and resolve environment variables (including cross-references)
        Map<String, String> processedEnv = substituteInEnvironmentMap(server.env(), systemEnv);
        
        // Build a combined environment for field substitution
        Map<String, String> combinedEnv = new java.util.HashMap<>(systemEnv != null ? systemEnv : java.util.Map.of());
        if (processedEnv != null) {
            combinedEnv.putAll(processedEnv);
        }
        
        // Substitute in all string fields using combined environment
        String substitutedType = substituteString(server.type(), combinedEnv);
        String substitutedUrl = substituteString(server.url(), combinedEnv);
        
        Map<String, String> substitutedHeaders = server.headers() != null
            ? server.headers().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    entry -> substituteString(entry.getKey(), combinedEnv),
                    entry -> substituteString(entry.getValue(), combinedEnv)
                ))
            : server.headers();
        
        return new HttpServer(substitutedType, substitutedUrl, substitutedHeaders, processedEnv);
    }
    
    /**
     * Performs environment variable substitution within environment variables themselves.
     * Uses system environment variables to resolve placeholders in the server's env map.
     * 
     * @param env the original environment map from the server configuration
     * @param systemEnv the system environment variables
     * @return a new map with placeholders resolved using system environment variables
     */
    private static Map<String, String> substituteInEnvironmentMap(Map<String, String> env, Map<String, String> systemEnv) {
        if (env == null || env.isEmpty()) {
            return env;
        }
        
        // Create a mutable copy for processing
        Map<String, String> processedEnv = new java.util.HashMap<>(env);
        
        // Iteratively resolve cross-references using both provided env and progressively resolved values
        final int maxIterations = 10;
        for (int i = 0; i < maxIterations; i++) {
            boolean changed = false;
            // Build a lookup that prefers already-resolved values
            Map<String, String> lookup = new java.util.HashMap<>(systemEnv != null ? systemEnv : java.util.Map.of());

            for (Map.Entry<String, String> entry : processedEnv.entrySet()) {
                String before = entry.getValue();
                String after = substituteString(before, lookup);
                if (!java.util.Objects.equals(before, after)) {
                    entry.setValue(after);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        
        return processedEnv;
    }
    
    /**
     * Performs environment variable substitution in a single string.
     * Replaces all occurrences of {VAR_NAME} with the corresponding value from the env map.
     * 
     * @param input the input string that may contain placeholders
     * @param env the environment variables map
     * @return the string with placeholders replaced, or the original string if no placeholders found
     */
    public static String substituteString(String input, Map<String, String> env) {
        if (input == null || env == null || env.isEmpty()) {
            return input;
        }
        
        Matcher matcher = ENV_VAR_PATTERN.matcher(input);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = env.get(varName);
            
            if (replacement != null) {
                // Escape special regex characters in the replacement
                replacement = Matcher.quoteReplacement(replacement);
                matcher.appendReplacement(result, replacement);
            } else {
                // Keep the original placeholder if no replacement found
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Checks if a string contains environment variable placeholders.
     * 
     * @param input the string to check
     * @return true if the string contains {VAR_NAME} patterns, false otherwise
     */
    public static boolean hasPlaceholders(String input) {
        return input != null && ENV_VAR_PATTERN.matcher(input).find();
    }
    
    /**
     * Extracts all placeholder variable names from a string.
     * 
     * @param input the string to analyze
     * @return a set of variable names found in placeholders
     */
    public static java.util.Set<String> extractPlaceholders(String input) {
        if (input == null) {
            return java.util.Set.of();
        }
        
        Matcher matcher = ENV_VAR_PATTERN.matcher(input);
        java.util.Set<String> placeholders = new java.util.HashSet<>();
        
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        
        return placeholders;
    }
}