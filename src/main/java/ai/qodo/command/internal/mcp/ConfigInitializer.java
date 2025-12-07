/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.mcp;

import java.io.IOException;

/**
 * Interface for reading and initializing configuration files.
 * This interface extends the basic configuration reading with initialization
 * that performs environment variable substitution and other setup tasks.
 * 
 * @param <T> the type of configuration object to be parsed and initialized
 */
public interface ConfigInitializer<T> {
    
    /**
     * Reads configuration from the given source and initializes it.
     * This method performs the following steps:
     * 1. Reads and parses the configuration
     * 2. Performs environment variable substitution (replaces {VAR_NAME} with actual values)
     * 3. Validates the configuration
     * 4. Returns the initialized configuration
     * 
     * @param source the configuration source
     * @return the parsed and initialized configuration object
     * @throws IOException if the configuration cannot be read, parsed, or initialized
     */
    T init(ConfigSource source) throws IOException;
    
    /**
     * Starts the configuration by reading and initializing it.
     * This is an alias for init() to provide a more intuitive API.
     * 
     * @param source the configuration source
     * @return the parsed and initialized configuration object
     * @throws IOException if the configuration cannot be read, parsed, or initialized
     */
    default T start(ConfigSource source) throws IOException {
        return init(source);
    }
}