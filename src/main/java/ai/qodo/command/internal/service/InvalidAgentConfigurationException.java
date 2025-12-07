/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when an agent configuration is malformed or incomplete.
 * This indicates that while an agent command exists, it's missing required fields
 * or has invalid values that prevent it from being used.
 */
public class InvalidAgentConfigurationException extends CommandException {
    
    private final String commandName;
    private final List<String> validationErrors;
    
    /**
     * Constructs a new InvalidAgentConfigurationException with the specified detail message.
     *
     * @param message the detail message explaining the configuration issue
     */
    public InvalidAgentConfigurationException(String message) {
        super(message);
        this.commandName = null;
        this.validationErrors = new ArrayList<>();
    }
    
    /**
     * Constructs a new InvalidAgentConfigurationException with command name and validation errors.
     *
     * @param message the detail message
     * @param commandName the name of the command with invalid configuration
     * @param validationErrors list of specific validation errors found
     */
    public InvalidAgentConfigurationException(String message, String commandName, List<String> validationErrors) {
        super(message);
        this.commandName = commandName;
        this.validationErrors = validationErrors != null ? new ArrayList<>(validationErrors) : new ArrayList<>();
    }
    
    /**
     * Constructs a new InvalidAgentConfigurationException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public InvalidAgentConfigurationException(String message, Throwable cause) {
        super(message, cause);
        this.commandName = null;
        this.validationErrors = new ArrayList<>();
    }
    
    /**
     * Gets the name of the command with invalid configuration.
     *
     * @return the command name, or null if not specified
     */
    public String getCommandName() {
        return commandName;
    }
    
    /**
     * Gets the list of validation errors found in the configuration.
     *
     * @return an unmodifiable list of validation error messages
     */
    public List<String> getValidationErrors() {
        return Collections.unmodifiableList(validationErrors);
    }
    
    /**
     * Returns a detailed message including all validation errors.
     *
     * @return the detailed error message
     */
    @Override
    public String getMessage() {
        if (validationErrors.isEmpty()) {
            return super.getMessage();
        }
        
        StringBuilder sb = new StringBuilder(super.getMessage());
        sb.append("\nValidation errors:");
        for (String error : validationErrors) {
            sb.append("\n  - ").append(error);
        }
        return sb.toString();
    }
}
