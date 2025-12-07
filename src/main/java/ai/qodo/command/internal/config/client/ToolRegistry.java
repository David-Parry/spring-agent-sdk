/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.config.client;

import ai.qodo.command.internal.config.BlockedToolsConfiguration;
import ai.qodo.command.internal.mcp.McpServerInitialized;
import ai.qodo.command.internal.mcp.ServerTool;
import ai.qodo.command.internal.metrics.McpMetrics;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing MCP tools and their mappings.
 * Handles tool filtering based on blocked tools configuration.
 */
@Component
public class ToolRegistry {
    private static final Logger logger = LoggerFactory.getLogger(ToolRegistry.class);
    private final BlockedToolsConfiguration blockedToolsConfiguration;
    private final McpMetrics mcpMetrics;
    private final Map<ServerTool, McpSchema.Tool> toolMap = new ConcurrentHashMap<>();

    public ToolRegistry(BlockedToolsConfiguration blockedToolsConfiguration, McpMetrics mcpMetrics) {
        this.blockedToolsConfiguration = blockedToolsConfiguration;
        this.mcpMetrics = mcpMetrics;
    }

    /**
     * Gets the complete tool map.
     *
     * @return map of server tools to MCP schema tools
     */
    public Map<ServerTool, McpSchema.Tool> getToolMap() {
        return toolMap;
    }

    /**
     * Filters tools based on blocked tools configuration.
     * Removes any tools that are in the blocked list.
     *
     * @return filtered map of tools
     */
    public Map<ServerTool, McpSchema.Tool> filterTools() {
        final Map<ServerTool, McpSchema.Tool> filtered = new HashMap<>(toolMap);
        blockedToolsConfiguration.getParsedBlockedTools().forEach(tool -> {
            String[] parts = tool.split(":", 2);
            if (parts.length == 2) {
                String serverName = parts[0].trim();
                String toolName = parts[1].trim();
                ServerTool serverTool = new ServerTool(serverName, toolName);
                filtered.remove(serverTool);
            }
        });

        return filtered;
    }

    /**
     * Refreshes the tool map from the provided clients.
     *
     * @param clients map of server names to initialized MCP servers
     */
    public void refreshToolMap(Map<String, McpServerInitialized> clients) {
        Map<ServerTool, McpSchema.Tool> refreshed = createToolMap(clients);
        this.toolMap.putAll(refreshed);
        mcpMetrics.setRegisteredTools(this.toolMap.size());
        logger.debug("Tool map refreshed with {} tools", refreshed.size());
    }

    /**
     * Creates a tool map from the provided clients.
     *
     * @param clients map of server names to initialized MCP servers
     * @return map of server tools to MCP schema tools
     */
    private Map<ServerTool, McpSchema.Tool> createToolMap(Map<String, McpServerInitialized> clients) {
        var toolMap = new ConcurrentHashMap<ServerTool, McpSchema.Tool>();
        for (String serverName : clients.keySet()) {
            List<McpSchema.Tool> tools = clients.get(serverName).tools();
            for (McpSchema.Tool tool : tools) {
                toolMap.put(new ServerTool(serverName, tool.name()), tool);
            }
        }
        return toolMap;
    }

    /**
     * Clears all tools from the registry.
     */
    public void clear() {
        toolMap.clear();
        mcpMetrics.setRegisteredTools(0);
    }
}
