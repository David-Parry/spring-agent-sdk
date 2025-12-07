/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.mcp;

import java.io.IOException;
import java.io.InputStream;

/**
 * Abstraction for configuration sources.
 * Follows the Strategy pattern to allow different input sources.
 */
public interface ConfigSource {
    
    /**
     * Opens an input stream to read the configuration data.
     * 
     * @return an InputStream containing the configuration data
     * @throws IOException if the source cannot be opened
     */
    InputStream openStream() throws IOException;
    
    /**
     * Gets a description of this configuration source for error reporting.
     * 
     * @return a human-readable description of the source
     */
    String getDescription();
}