/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;

/**
 * Generic JSON-based configuration reader implementation.
 * Follows the Single Responsibility Principle by focusing only on JSON parsing.
 * Uses Dependency Inversion Principle by depending on ObjectMapper abstraction.
 * 
 * @param <T> the type of configuration object to be parsed
 */
public class JsonConfigReader<T> implements ConfigReader<T> {
    
    private final ObjectMapper objectMapper;
    private final Class<T> configType;
    
    /**
     * Creates a new JsonConfigReader with the default ObjectMapper.
     * 
     * @param configType the class type of the configuration object
     */
    public JsonConfigReader(Class<T> configType) {
        this(new ObjectMapper(), configType);
    }
    
    /**
     * Creates a new JsonConfigReader with a custom ObjectMapper.
     * Follows Dependency Inversion Principle by accepting ObjectMapper as dependency.
     * 
     * @param objectMapper the ObjectMapper to use for JSON parsing
     * @param configType the class type of the configuration object
     */
    public JsonConfigReader(ObjectMapper objectMapper, Class<T> configType) {
        this.objectMapper = objectMapper;
        this.configType = configType;
    }
    
    @Override
    public T readConfig(ConfigSource source) throws IOException {
        try (InputStream inputStream = source.openStream()) {
            return objectMapper.readValue(inputStream, configType);
        } catch (IOException e) {
            throw new IOException("Failed to parse configuration from " + source.getDescription(), e);
        }
    }
}