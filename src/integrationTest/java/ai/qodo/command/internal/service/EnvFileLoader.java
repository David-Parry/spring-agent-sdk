/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads environment variables from a .env file and configures the Spring application context.
 * This class is designed to be used as an ApplicationContextInitializer for integration tests
 * to load Jira configuration from a .env file.
 * 
 * Usage:
 * Add @ContextConfiguration(initializers = EnvFileLoader.class) to your test class
 */
public class EnvFileLoader  {
    
    private static final Logger logger = LoggerFactory.getLogger(EnvFileLoader.class);
    private static final String ENV_FILE_NAME = ".env";


    
    /**
     * Finds the .env file in the project root directory.
     * Searches from the current working directory upwards.
     */
    public Path findEnvFile() {
        // Try current directory first
        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path envFile = currentDir.resolve(ENV_FILE_NAME);
        
        if (Files.exists(envFile)) {
            return envFile;
        }
        
        // Try parent directories (up to 3 levels)
        Path parent = currentDir;
        for (int i = 0; i < 3; i++) {
            parent = parent.getParent();
            if (parent == null) {
                break;
            }
            envFile = parent.resolve(ENV_FILE_NAME);
            if (Files.exists(envFile)) {
                return envFile;
            }
        }
        
        return null;
    }
    
    /**
     * Loads key-value pairs from the .env file.
     * Supports basic .env format: KEY=VALUE
     * Ignores comments (lines starting with #) and empty lines.
     */
    public Map<String, String> loadEnvFile(Path envFilePath) {
        Map<String, String> properties = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(envFilePath.toFile()))) {
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Parse KEY=VALUE format
                int equalsIndex = line.indexOf('=');
                if (equalsIndex > 0) {
                    String key = line.substring(0, equalsIndex).trim();
                    String value = line.substring(equalsIndex + 1).trim();
                    
                    // Remove quotes if present
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    
                    properties.put(key, value);
                    logger.debug("Loaded property: {} = {}", key, maskSensitiveValue(key, value));
                } else {
                    logger.warn("Invalid line format at line {}: {}", lineNumber, line);
                }
            }
        } catch (IOException e) {
            logger.error("Error reading .env file: {}", envFilePath, e);
        }
        
        return properties;
    }
    


    /**
     * Masks sensitive values in logs (API tokens, passwords, etc.)
     */
    private String maskSensitiveValue(String key, String value) {
        if (key.toUpperCase().contains("TOKEN") ||
            key.toUpperCase().contains("PASSWORD") ||
            key.toUpperCase().contains("SECRET")) {
            if (value.length() > 8) {
                return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
            }
            return "****";
        }
        return value;
    }
}
