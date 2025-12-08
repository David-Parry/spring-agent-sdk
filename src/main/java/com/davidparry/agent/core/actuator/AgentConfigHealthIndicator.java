/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.actuator;

import com.davidparry.agent.core.mcp.AgentCommand;
import com.davidparry.agent.core.mcp.AgentConfig;
import com.davidparry.agent.core.service.AgentConfigManager;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Health indicator for agent configuration status.
 * Provides detailed information about the loaded agent commands and their validity.
 */
@Component
public class AgentConfigHealthIndicator implements HealthIndicator {

    private final AgentConfigManager agentConfigManager;

    /**
     * Constructs an AgentConfigHealthIndicator with the given agent config manager
     * @param agentConfigManager The agent configuration manager
     */
    public AgentConfigHealthIndicator(AgentConfigManager agentConfigManager) {
        this.agentConfigManager = agentConfigManager;
    }

    @Override
    public Health health() {
        try {
            AgentConfig config = agentConfigManager.getAgentConfig();
            
            if (config == null) {
                return Health.down()
                    .withDetail("error", "Agent configuration is not loaded")
                    .build();
            }
            
            if (config.commands() == null || config.commands().isEmpty()) {
                return Health.down()
                    .withDetail("error", "No agent commands configured")
                    .withDetail("version", config.version())
                    .build();
            }
            
            Map<String, Object> details = new HashMap<>();
            details.put("version", config.version());
            details.put("totalCommands", config.commands().size());
            
            List<String> commandNames = new ArrayList<>(config.commands().keySet());
            details.put("commands", commandNames);
            
            // Check for any commands with potential issues
            List<String> commandsWithIssues = new ArrayList<>();
            for (Map.Entry<String, AgentCommand> entry : config.commands().entrySet()) {
                AgentCommand command = entry.getValue();
                if (command == null || 
                    command.instructions() == null || command.instructions().trim().isEmpty() ||
                    command.systemPrompt() == null || command.systemPrompt().trim().isEmpty()) {
                    commandsWithIssues.add(entry.getKey());
                }
            }
            
            if (!commandsWithIssues.isEmpty()) {
                details.put("commandsWithIssues", commandsWithIssues);
                details.put("warning", "Some commands may have incomplete configuration");
                return Health.up()
                    .withDetails(details)
                    .build();
            }
            
            return Health.up()
                .withDetails(details)
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", "Failed to check agent configuration")
                .withDetail("exception", e.getMessage())
                .build();
        }
    }
}
