/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.service;

import com.davidparry.agent.core.api.OutputJsonSchema;
import com.davidparry.agent.core.api.OutputSchema;
import com.davidparry.agent.core.config.AgentLoaderConfig;
import com.davidparry.agent.core.mcp.AgentCommand;
import com.davidparry.agent.core.mcp.AgentConfig;
import com.davidparry.agent.core.mcp.McpConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for loading and parsing agent configuration from YAML files
 */
@Service
public class AgentConfigService {

    private static final Logger logger = LoggerFactory.getLogger(AgentConfigService.class);

    private final AgentLoaderConfig agentLoaderConfig;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper yamlMapper;
    private final ObjectMapper objectMapper;

    /**
     * Constructs an AgentConfigService with the required dependencies
     * @param agentLoaderConfig Configuration for agent loading
     * @param resourceLoader Spring resource loader for loading configuration files
     * @param objectMapper Object mapper for JSON processing
     */
    @Autowired
    public AgentConfigService(AgentLoaderConfig agentLoaderConfig, ResourceLoader resourceLoader,
                              ObjectMapper objectMapper) {
        this.agentLoaderConfig = agentLoaderConfig;
        this.resourceLoader = resourceLoader;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.objectMapper = objectMapper;
    }

    /**
     * Loads and parses the agent configuration from the configured file path
     *
     * @return AgentConfig object representing the parsed YAML configuration
     * @throws IOException if the file cannot be read or parsed
     */
    public AgentConfig loadAgentConfig() throws Exception {
        String configFilePath = agentLoaderConfig.getConfigFile();
        logger.info("Loading agent configuration from: {}", configFilePath);

        try {
            Resource resource = resourceLoader.getResource(configFilePath);

            if (!resource.exists()) {
                throw new IOException("Agent configuration file not found: " + configFilePath);
            }

            try (InputStream inputStream = resource.getInputStream()) {
                AgentConfig config = yamlMapper.readValue(inputStream, AgentConfig.class);
                logger.info("Successfully loaded agent configuration with version: {} and {} commands",
                            config.version(), config
                        .commands()
                        .size());
                return loadOutputSchemas(config);
            }

        } catch (Exception e) {
            logger.error("Failed to load agent configuration from {}: {}", configFilePath, e.getMessage(), e);
            throw e;
        }
    }


    /**
     * Loads output schemas for all commands in the configuration
     * @param config The agent configuration
     * @return Updated agent configuration with loaded output schemas
     * @throws Exception if schema loading fails
     */
    protected AgentConfig loadOutputSchemas(AgentConfig config) throws Exception {
        Map<String, AgentCommand> commands = new HashMap<>();
        for (String commandName : config.commands().keySet()) {
            AgentCommand command = config.commands().get(commandName);
            OutputSchema outputSchema = createOutputSchema(command.outputSchemaString(), commandName);
            McpConfig mcpConfig = objectMapper.readValue(command.mcpServers(), McpConfig.class);
            commands.put(commandName, new AgentCommand(command, outputSchema, config.systemPrompt(), config.version()
                    , commandName, mcpConfig));
        }
        return new AgentConfig(config.version(), config.systemPrompt(), commands);
    }


    /**
     * Creates an output schema from a JSON schema string
     * @param schema The JSON schema string
     * @param commandName The name of the command
     * @return The created output schema
     * @throws Exception if schema creation fails
     */
    protected OutputSchema createOutputSchema(String schema, String commandName) throws Exception {
        OutputJsonSchema outputJsonSchema = null;
        try {
            Map<String, Object> nodes = objectMapper.readValue(schema, Map.class);

            // Ensure JSON Schema draft 2020-12 compliance
            nodes.put("type", "object");
            nodes.put("title", "Output" + toCamelCase(commandName));

            // Add $schema if not present to ensure draft 2020-12 compliance
            if (!nodes.containsKey("$schema")) {
                nodes.put("$schema", "https://json-schema.org/draft/2020-12/schema");
            }

            // Validate that required properties are properly defined
            validateSchemaStructure(nodes, commandName);

            outputJsonSchema = new OutputJsonSchema("output_" + commandName, true, nodes);
        } catch (JsonProcessingException e) {
            logger.error("Check your output_schema in Agent.yml file, you must fix this to work with Agent calls " +
                                 "commandName: {}", commandName, e);
            throw new Exception("Output schema is invalid and will not work with Agent calls");
        }

        return new OutputSchema("json_schema", outputJsonSchema);
    }

    /**
     * Validates the schema structure for JSON Schema draft 2020-12 compliance
     *
     * @param schema      the schema map to validate
     * @param commandName the command name for logging
     */
    private void validateSchemaStructure(Map<String, Object> schema, String commandName) {
        // Check if properties exist and validate their structure
        if (schema.containsKey("properties")) {
            @SuppressWarnings("unchecked") Map<String, Object> properties = (Map<String, Object>) schema.get(
                    "properties");

            for (Map.Entry<String, Object> property : properties.entrySet()) {
                @SuppressWarnings("unchecked") Map<String, Object> propertyDef =
                        (Map<String, Object>) property.getValue();

                // Validate array types have items definition
                if ("array".equals(propertyDef.get("type")) && !propertyDef.containsKey("items")) {
                    logger.warn("Property '{}' in command '{}' is defined as array but missing 'items' definition. " +
                                        "This may cause validation issues.", property.getKey(), commandName);
                }

                // Validate type values are lowercase (JSON Schema requirement)
                Object typeValue = propertyDef.get("type");
                if (typeValue instanceof String type) {
                    if (!type.equals(type.toLowerCase())) {
                        logger.warn("Property '{}' in command '{}' has type '{}' which should be lowercase for JSON " +
                                            "Schema compliance", property.getKey(), commandName, type);
                        propertyDef.put("type", type.toLowerCase());
                    }
                }
            }
        }
    }

    /**
     * Converts underscore-separated string to camelCase
     *
     * @param input the underscore-separated string
     * @return camelCase string
     */
    private String toCamelCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String[] parts = input.split("_");
        StringBuilder result = new StringBuilder(parts[0]);

        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                result.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) {
                    result.append(parts[i].substring(1));
                }
            }
        }

        return result.toString();
    }


    /**
     * Gets the configured agent config file path
     *
     * @return the configured file path
     */
    public String getConfigFilePath() {
        return agentLoaderConfig.getConfigFile();
    }
}