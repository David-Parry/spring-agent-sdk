/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.service;

/**
 * Unchecked exception used to signal errors during command processing and messaging operations.
 */
public class CommandException extends RuntimeException {
    /**
     * Creates a new CommandException with a message.
     *
     * @param message the error message
     */
    public CommandException(String message) {
        super(message);
    }

    /**
     * Creates a new CommandException with a message and a cause.
     *
     * @param message the error message
     * @param cause   the underlying cause of the error
     */
    public CommandException(String message, Throwable cause) {
        super(message, cause);
    }
}
