/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.mcp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Factory class for creating different types of configuration sources.
 * Follows the Factory pattern to encapsulate source creation logic.
 */
public final class ConfigSources {
    
    private ConfigSources() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Creates a configuration source from a file path.
     * 
     * @param filePath the path to the configuration file
     * @return a ConfigSource for the file
     */
    public static ConfigSource fromPath(Path filePath) {
        return new PathConfigSource(filePath);
    }
    
    /**
     * Creates a configuration source from a File object.
     * 
     * @param file the configuration file
     * @return a ConfigSource for the file
     */
    public static ConfigSource fromFile(File file) {
        return new FileConfigSource(file);
    }
    
    /**
     * Creates a configuration source from a JSON string.
     * 
     * @param jsonContent the JSON content as a string
     * @return a ConfigSource for the string content
     */
    public static ConfigSource fromString(String jsonContent) {
        return new StringConfigSource(jsonContent);
    }
    
    /**
     * Creates a configuration source from an InputStream.
     * 
     * @param inputStream the input stream containing configuration data
     * @param description a description of the source for error reporting
     * @return a ConfigSource for the input stream
     */
    public static ConfigSource fromInputStream(InputStream inputStream, String description) {
        return new InputStreamConfigSource(inputStream, description);
    }
    
    private static class PathConfigSource implements ConfigSource {
        private final Path filePath;
        
        PathConfigSource(Path filePath) {
            this.filePath = filePath;
        }
        
        @Override
        public InputStream openStream() throws IOException {
            return Files.newInputStream(filePath);
        }
        
        @Override
        public String getDescription() {
            return "Path: " + filePath.toString();
        }
    }
    
    private static class FileConfigSource implements ConfigSource {
        private final File file;
        
        FileConfigSource(File file) {
            this.file = file;
        }
        
        @Override
        public InputStream openStream() throws IOException {
            return new FileInputStream(file);
        }
        
        @Override
        public String getDescription() {
            return "File: " + file.getAbsolutePath();
        }
    }
    
    private static class StringConfigSource implements ConfigSource {
        private final String content;
        
        StringConfigSource(String content) {
            this.content = content;
        }
        
        @Override
        public InputStream openStream() {
            return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }
        
        @Override
        public String getDescription() {
            return "String content (" + content.length() + " characters)";
        }
    }
    
    private static class InputStreamConfigSource implements ConfigSource {
        private final InputStream inputStream;
        private final String description;
        
        InputStreamConfigSource(InputStream inputStream, String description) {
            this.inputStream = inputStream;
            this.description = description;
        }
        
        @Override
        public InputStream openStream() {
            return inputStream;
        }
        
        @Override
        public String getDescription() {
            return description;
        }
    }
}