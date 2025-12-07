/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.service;

/**
 * Exception thrown when a message type does not have a corresponding agent command configuration.
 * This indicates a configuration issue where the system received a message type that it doesn't
 * know how to process because no agent command is defined for it.
 */
public class MissingAgentCommandException extends CommandException {
    
    private final String messageType;
    
    /**
     * Constructs a new MissingAgentCommandException with the specified detail message.
     *
     * @param message the detail message explaining which message type is missing
     */
    public MissingAgentCommandException(String message) {
        super(message);
        this.messageType = null;
    }
    
    /**
     * Constructs a new MissingAgentCommandException with the specified detail message and message type.
     *
     * @param message the detail message explaining the issue
     * @param messageType the message type that is missing an agent command configuration
     */
    public MissingAgentCommandException(String message, String messageType) {
        super(message);
        this.messageType = messageType;
    }
    
    /**
     * Constructs a new MissingAgentCommandException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public MissingAgentCommandException(String message, Throwable cause) {
        super(message, cause);
        this.messageType = null;
    }
    
    /**
     * Gets the message type that is missing an agent command configuration.
     *
     * @return the message type, or null if not specified
     */
    public String getMessageType() {
        return messageType;
    }
}
