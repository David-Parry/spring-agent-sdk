/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Configuration class that manages blocked tools from application.yml with environment variable support.
 * The blockedTools property can be set via QODO_BLOCKED_TOOLS environment variable as a comma-separated string.
 */
@Configuration
@ConfigurationProperties(prefix = "qodo")
public class BlockedToolsConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(BlockedToolsConfiguration.class);
    private static final String DELIMITER = ",";

    private String blockedTools = "";
    private Set<String> parsedBlockedTools = new HashSet<>();

    public BlockedToolsConfiguration() {
    }

    /**
     * Initializes the blocked tools set by parsing the comma-separated string from application.yml.
     * This method is called after the bean is constructed and all properties are set.
     */
    @PostConstruct
    public void initializeBlockedTools() {
        logger.info("Initializing blocked tools configuration...");

        // Parse the comma-separated string from application.yml (which may come from environment variable)
        Set<String> allBlockedTools = new HashSet<>();
        if (blockedTools != null && !blockedTools.trim().isEmpty()) {
            List<String> tools = Arrays.stream(blockedTools.split(DELIMITER))
                    .map(String::trim)
                    .filter(tool -> !tool.isEmpty())
                    .collect(Collectors.toList());
            
            allBlockedTools.addAll(tools);
            logger.debug("Blocked tools from configuration: {}", tools);
        }

        // Store the parsed result
        this.parsedBlockedTools = Collections.unmodifiableSet(allBlockedTools);
        
        logger.info("Blocked tools initialized. Total blocked tools: {} - Tools: {}", 
                   parsedBlockedTools.size(), parsedBlockedTools);
    }

    /**
     * Gets the blocked tools string from application.yml (used by Spring Boot for property binding).
     * 
     * @return the comma-separated string of blocked tools from configuration
     */
    public String getBlockedTools() {
        return blockedTools;
    }

    /**
     * Sets the blocked tools string from application.yml (used by Spring Boot for property binding).
     * 
     * @param blockedTools the comma-separated string of blocked tools from configuration
     */
    public void setBlockedTools(String blockedTools) {
        this.blockedTools = blockedTools != null ? blockedTools : "";
    }

    /**
     * Gets the parsed set of blocked tools from the configuration.
     * This is the method that should be used by other components to check for blocked tools.
     * 
     * @return an unmodifiable set containing all blocked tools
     */
    public Set<String> getParsedBlockedTools() {
        return parsedBlockedTools;
    }

    /**
     * Checks if a specific tool is blocked.
     * 
     * @param toolName the name of the tool to check
     * @return true if the tool is blocked, false otherwise
     */
    public boolean isToolBlocked(String toolName) {
        return parsedBlockedTools.contains(toolName);
    }

    /**
     * Gets the count of blocked tools.
     * 
     * @return the total number of blocked tools
     */
    public int getBlockedToolsCount() {
        return parsedBlockedTools.size();
    }
}