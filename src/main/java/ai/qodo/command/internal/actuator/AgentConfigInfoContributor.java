/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.actuator;

import ai.qodo.command.internal.mcp.AgentCommand;
import ai.qodo.command.internal.mcp.AgentConfig;
import ai.qodo.command.internal.service.AgentConfigManager;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contributes agent configuration information to the /actuator/info endpoint.
 * Provides a summary of configured agent commands without exposing sensitive details.
 */
@Component
public class AgentConfigInfoContributor implements InfoContributor {

    private final AgentConfigManager agentConfigManager;

    /**
     * Constructs an AgentConfigInfoContributor with the given agent config manager
     * @param agentConfigManager The agent configuration manager
     */
    public AgentConfigInfoContributor(AgentConfigManager agentConfigManager) {
        this.agentConfigManager = agentConfigManager;
    }

    @Override
    public void contribute(Info.Builder builder) {
        try {
            AgentConfig config = agentConfigManager.getAgentConfig();
            
            if (config == null) {
                builder.withDetail("agentConfig", Map.of("status", "not loaded"));
                return;
            }
            
            Map<String, Object> configInfo = new HashMap<>();
            configInfo.put("version", config.version());
            configInfo.put("commandCount", config.commands() != null ? config.commands().size() : 0);
            
            if (config.commands() != null && !config.commands().isEmpty()) {
                List<Map<String, Object>> commandSummaries = new ArrayList<>();
                
                for (Map.Entry<String, AgentCommand> entry : config.commands().entrySet()) {
                    Map<String, Object> commandSummary = new HashMap<>();
                    commandSummary.put("name", entry.getKey());
                    
                    AgentCommand command = entry.getValue();
                    if (command != null) {
                        commandSummary.put("hasInstructions", command.instructions() != null && !command.instructions().trim().isEmpty());
                        commandSummary.put("hasSystemPrompt", command.systemPrompt() != null && !command.systemPrompt().trim().isEmpty());
                        commandSummary.put("hasMcpServers", command.mcpServers() != null && !command.mcpServers().trim().isEmpty());
                        commandSummary.put("hasTools", command.tools() != null && !command.tools().isEmpty());
                        commandSummary.put("model", command.model() != null ? command.model() : "default");
                    } else {
                        commandSummary.put("status", "null");
                    }
                    
                    commandSummaries.add(commandSummary);
                }
                
                configInfo.put("commands", commandSummaries);
            }
            
            builder.withDetail("agentConfig", configInfo);
            
        } catch (Exception e) {
            builder.withDetail("agentConfig", Map.of(
                "status", "error",
                "error", e.getMessage()
            ));
        }
    }
}
