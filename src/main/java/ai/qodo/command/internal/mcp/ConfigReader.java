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
 * Generic interface for reading and parsing configuration files.
 * Follows the Single Responsibility Principle by focusing only on reading configurations.
 * 
 * @param <T> the type of configuration object to be parsed
 */
public interface ConfigReader<T> {
    
    /**
     * Reads and parses configuration from the given source.
     * 
     * @param source the configuration source
     * @return the parsed configuration object
     * @throws IOException if the configuration cannot be read or parsed
     */
    T readConfig(ConfigSource source) throws IOException;
}