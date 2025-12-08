/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.service;

import com.davidparry.agent.core.mcp.AgentCommand;
import com.davidparry.agent.core.mcp.AgentConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manager component that loads and caches the agent configuration
 */
@Component
public class AgentConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(AgentConfigManager.class);

    private final AgentConfigService agentConfigService;
    private AgentConfig agentConfig;

    /**
     * Constructs an AgentConfigManager with the given agent config service
     * @param agentConfigService The service for loading agent configurations
     */
    @Autowired
    public AgentConfigManager(AgentConfigService agentConfigService) {
        this.agentConfigService = agentConfigService;
    }

    /**
     * Loads the agent configuration after dependency injection is complete.
     * This ensures the configuration is available for other components during startup.
     */
    @PostConstruct
    public void loadAgentConfiguration() {
        String configFilePath = agentConfigService.getConfigFilePath();
        logger.info("Loading agent configuration from: {}", configFilePath);

        try {
            this.agentConfig = agentConfigService.loadAgentConfig();
            logger.info("Successfully loaded agent configuration with version: {} and {} commands",
                        agentConfig.version(), agentConfig
                    .commands()
                    .size());
            
            // Validate the loaded configuration
            validateConfiguration();
            
        } catch (Exception e) {
            logger.error("Failed to load agent configuration: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to load agent configuration, Cannot continue till agents are valid", e);
        }
    }
    
    /**
     * Validates the loaded agent configuration to ensure all commands have required fields.
     * Logs warnings for any issues found but doesn't fail startup.
     */
    private void validateConfiguration() {
        if (agentConfig == null || agentConfig.commands() == null) {
            logger.warn("Agent configuration is null or has no commands");
            return;
        }
        
        Map<String, AgentCommand> commands = agentConfig.commands();
        logger.info("Validating {} agent command(s)", commands.size());
        
        int validCommands = 0;
        int invalidCommands = 0;
        
        for (Map.Entry<String, AgentCommand> entry : commands.entrySet()) {
            String commandName = entry.getKey();
            AgentCommand command = entry.getValue();
            
            List<String> errors = validateAgentCommand(commandName, command);
            
            if (errors.isEmpty()) {
                validCommands++;
                logger.debug("Agent command '{}' is valid", commandName);
            } else {
                invalidCommands++;
                logger.warn("Agent command '{}' has validation issues:", commandName);
                for (String error : errors) {
                    logger.warn("  - {}", error);
                }
            }
        }
        
        logger.info("Configuration validation complete: {} valid, {} with issues", validCommands, invalidCommands);
        
        if (invalidCommands > 0) {
            logger.warn("Some agent commands have validation issues. They may fail at runtime.");
        }
    }
    
    /**
     * Validates a single agent command and returns a list of validation errors.
     *
     * @param commandName the name of the command
     * @param command the agent command to validate
     * @return list of validation error messages (empty if valid)
     */
    private List<String> validateAgentCommand(String commandName, AgentCommand command) {
        List<String> errors = new ArrayList<>();
        
        if (command == null) {
            errors.add("Command is null");
            return errors;
        }
        
        // Validate required fields
        if (command.name() == null || command.name().trim().isEmpty()) {
            errors.add("Missing or empty 'name' field");
        }
        
        if (command.instructions() == null || command.instructions().trim().isEmpty()) {
            errors.add("Missing or empty 'instructions' field");
        }
        
        if (command.systemPrompt() == null || command.systemPrompt().trim().isEmpty()) {
            errors.add("Missing or empty 'systemPrompt' field");
        }
        
        // Validate name consistency
        if (command.name() != null && !command.name().equals(commandName)) {
            errors.add(String.format("Command name mismatch: key='%s', name='%s'", commandName, command.name()));
        }
        
        return errors;
    }
    
    /**
     * Validates a specific agent command by name and throws an exception if invalid.
     *
     * @param messageType the message type / command name to validate
     * @throws InvalidAgentConfigurationException if the command is invalid
     */
    public void validateCommandOrThrow(String messageType) {
        if (agentConfig == null || agentConfig.commands() == null) {
            throw new InvalidAgentConfigurationException("Agent configuration is not loaded");
        }
        
        AgentCommand command = agentConfig.commands().get(messageType);
        if (command == null) {
            throw new MissingAgentCommandException(
                String.format("No agent command configured for message type '%s'", messageType),
                messageType
            );
        }
        
        List<String> errors = validateAgentCommand(messageType, command);
        if (!errors.isEmpty()) {
            throw new InvalidAgentConfigurationException(
                String.format("Agent command '%s' has invalid configuration", messageType),
                messageType,
                errors
            );
        }
    }


    /**
     * Gets the loaded agent configuration
     *
     * @return the loaded AgentConfig, or null if not yet loaded or failed to load
     */
    public AgentConfig getAgentConfig() {
        return agentConfig;
    }


}