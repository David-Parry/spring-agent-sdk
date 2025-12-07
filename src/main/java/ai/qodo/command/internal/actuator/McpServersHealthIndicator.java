/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.actuator;

import ai.qodo.command.internal.config.MCPClientInitializer;
import ai.qodo.command.internal.mcp.AgentMcpServers;
import ai.qodo.command.internal.mcp.McpServer;
import ai.qodo.command.internal.mcp.McpServerInitialized;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class McpServersHealthIndicator implements HealthIndicator {

    private final MCPClientInitializer initializer;

    @Autowired
    public McpServersHealthIndicator(MCPClientInitializer initializer) {
        this.initializer = initializer;
    }

    @Override
    public Health health() {
        Map<String, String> statusDetails = new HashMap<>();
        boolean allUp = true;

        // Check clients from mcp.json
        allUp = checkClients(initializer.getClientsByName(), statusDetails, "", allUp);

        // Check no tools clients from mcp.json by recreating
        for (Map.Entry<String, McpServer> entry : initializer.getNoToolsClient().entrySet()) {
            String serverName = entry.getKey();
            McpServer config = entry.getValue();
            allUp = checkSingleServer(serverName, config, statusDetails, "", allUp);
        }

        // Check agent-specific clients
        for (Map.Entry<String, AgentMcpServers> agentEntry : initializer.getAllAgentMcpServers().entrySet()) {
            String agentName = agentEntry.getKey();
            allUp = checkClients(agentEntry.getValue().mcpServers(), statusDetails, agentName + "-", allUp);

            // Check agent-specific no tools servers
            for (Map.Entry<String, McpServer> noToolsEntry : agentEntry.getValue().noToolsServers().entrySet()) {
                String serverName = noToolsEntry.getKey();
                McpServer config = noToolsEntry.getValue();
                allUp = checkSingleServer(serverName, config, statusDetails, agentName + "-", allUp);
            }
        }

        Health.Builder builder = allUp ? Health.up() : Health.down();
        builder.withDetail("mcpServers", statusDetails);
        return builder.build();
    }

    private boolean checkClients(Map<String, McpServerInitialized> clients, Map<String, String> statusDetails,
                                 String prefix, boolean currentAllUp) {
        boolean allUp = currentAllUp;
        for (Map.Entry<String, McpServerInitialized> entry : clients.entrySet()) {
            String serverName = prefix + entry.getKey();
            McpSyncClient client = entry.getValue().client();
            try {
                McpSchema.ListToolsResult result = client.listTools();
                if (result != null) {
                    statusDetails.put(serverName, "UP");
                } else {
                    statusDetails.put(serverName, "DOWN");
                    allUp = false;
                }
            } catch (Exception e) {
                statusDetails.put(serverName, "DOWN: " + e.getMessage());
                allUp = false;
            }
        }
        return allUp;
    }

    private boolean checkSingleServer(String serverName, McpServer config, Map<String, String> statusDetails,
                                      String prefix, boolean currentAllUp) {
        boolean allUp = currentAllUp;
        try {
            McpSyncClient client = initializer.createClient(prefix + serverName, config, System.getProperty("user.home") + "/mcpRootHealthcheck");
            if (client != null) {
                McpSchema.ListToolsResult result = client.listTools();
                if (result != null) {
                    statusDetails.put(prefix + serverName, "UP");
                } else {
                    statusDetails.put(prefix + serverName, "DOWN");
                    allUp = false;
                }
                client.close();
            } else {
                statusDetails.put(prefix + serverName, "DOWN: could not create client");
                allUp = false;
            }
        } catch (Exception e) {
            statusDetails.put(prefix + serverName, "DOWN: " + e.getMessage());
            allUp = false;
        }
        return allUp;
    }
}
