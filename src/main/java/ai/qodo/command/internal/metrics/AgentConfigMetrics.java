/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Metrics for tracking agent configuration issues and usage.
 * Provides counters for missing agent commands and configuration errors.
 */
@Component
public class AgentConfigMetrics {

    private final MeterRegistry meterRegistry;
    
    // Counter for missing agent commands
    private static final String MISSING_COMMAND_COUNTER = "agent.config.missing.command";
    
    // Counter for invalid agent configurations
    private static final String INVALID_CONFIG_COUNTER = "agent.config.invalid";
    
    // Counter for successful command lookups
    private static final String COMMAND_LOOKUP_SUCCESS_COUNTER = "agent.config.lookup.success";

    /**
     * Constructs an AgentConfigMetrics with the given meter registry
     * @param meterRegistry The Micrometer meter registry for recording metrics
     */
    public AgentConfigMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Records a missing agent command for a specific message type.
     *
     * @param messageType the message type that was not found in configuration
     */
    public void recordMissingCommand(String messageType) {
        Counter.builder(MISSING_COMMAND_COUNTER)
            .description("Count of messages received with no corresponding agent command configuration")
            .tags(List.of(Tag.of("message_type", messageType)))
            .register(meterRegistry)
            .increment();
    }

    /**
     * Records an invalid agent configuration for a specific command.
     *
     * @param commandName the name of the command with invalid configuration
     * @param reason the reason for the validation failure
     */
    public void recordInvalidConfiguration(String commandName, String reason) {
        Counter.builder(INVALID_CONFIG_COUNTER)
            .description("Count of agent commands with invalid configuration")
            .tags(List.of(
                Tag.of("command_name", commandName),
                Tag.of("reason", reason)
            ))
            .register(meterRegistry)
            .increment();
    }

    /**
     * Records a successful command lookup.
     *
     * @param messageType the message type that was successfully found
     */
    public void recordSuccessfulLookup(String messageType) {
        Counter.builder(COMMAND_LOOKUP_SUCCESS_COUNTER)
            .description("Count of successful agent command lookups")
            .tags(List.of(Tag.of("message_type", messageType)))
            .register(meterRegistry)
            .increment();
    }
}
