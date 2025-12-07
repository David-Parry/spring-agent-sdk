/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.api;

/**
 * Enum containing commonly used string constants across the application.
 * Each enum value returns its exact string representation without any transformation.
 */
public enum StringConstants {
    EVENT_KEY("eventKey"),
    SESSION_ID("sessionId"),
    SUCCESS("success"),
    TYPE("type"),
    USER_HOME("user.home"),
    PROJECT_STRUCTURE("project_structure"),
    REQUEST_ID("requestId"),
    CHECKPOINT_ID("checkPointId"),
    MESSAGE_TYP("messageType"),
    LLM_CONVERSATION("llmConversation");


    private final String value;

    StringConstants(String value) {
        this.value = value;
    }

    /**
     * Returns the exact string value of this constant.
     * @return the string value
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns the exact string value when the enum is converted to string.
     * @return the string value
     */
    @Override
    public String toString() {
        return value;
    }
}
