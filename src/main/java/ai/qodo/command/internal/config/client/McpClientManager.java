/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.config.client;

import ai.qodo.command.internal.config.QodoProperties;
import ai.qodo.command.internal.config.transport.TransportFactory;
import ai.qodo.command.internal.mcp.*;
import ai.qodo.command.internal.metrics.McpMetrics;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of MCP clients.
 * Handles client creation, initialization, and shutdown.
 */
@Component
public class McpClientManager {
    private static final Logger logger = LoggerFactory.getLogger(McpClientManager.class);
    private final TransportFactory transportFactory;
    private final McpMetrics mcpMetrics;
    private final QodoProperties qodoProperties;
    private final Map<String, McpServer> noToolsClient = new ConcurrentHashMap<>();
    private final Map<String, McpServerInitialized> mcpServers = new ConcurrentHashMap<>();
    private final Map<String, AgentMcpServers> agentMcpServers = new HashMap<>();

    public McpClientManager(TransportFactory transportFactory, McpMetrics mcpMetrics, QodoProperties qodoProperties) {
        this.transportFactory = transportFactory;
        this.mcpMetrics = mcpMetrics;
        this.qodoProperties = qodoProperties;
    }

    /**
     * Gets all successfully initialized clients.
     *
     * @return map of server names to initialized clients
     */
    public Map<String, McpServerInitialized> getClientsByName() {
        return mcpServers;
    }

    /**
     * Gets servers that were configured but had no tools.
     *
     * @return map of server names to server configurations
     */
    public Map<String, McpServer> getNoToolsClient() {
        return noToolsClient;
    }

    /**
     * Gets MCP servers for a specific agent/command.
     *
     * @param commandName the command name
     * @return agent MCP servers configuration
     */
    public AgentMcpServers getAgentMcpServers(String commandName) {
        return Optional
                .ofNullable(agentMcpServers.get(commandName))
                .orElse(new AgentMcpServers(commandName, new HashMap<>(), new HashMap<>()));
    }

    /**
     * Gets all agent MCP servers.
     *
     * @return unmodifiable map of all agent MCP servers
     */
    public Map<String, AgentMcpServers> getAllAgentMcpServers() {
        return Collections.unmodifiableMap(agentMcpServers);
    }

    /**
     * Gets all server names that have been successfully initialized.
     *
     * @return list of server names
     */
    public List<String> getServerNames() {
        return new ArrayList<>(mcpServers.keySet());
    }

    /**
     * Loads MCP clients from server configuration.
     *
     * @param serverName the server name
     * @param serverConfig the server configuration
     * @param fullyQualifiedName the fully qualified path name
     */
    public void loadMcpClients(String serverName, McpServer serverConfig, String fullyQualifiedName) {
        try {
            noToolsClient.clear();
            McpSyncClient client = createClient(serverName, serverConfig, fullyQualifiedName);
            if (client != null) {
                mcpMetrics.incrementActiveServers();
                McpSchema.ListToolsResult listToolsResult = client.listTools();
                if (listToolsResult != null && listToolsResult.tools() != null && !listToolsResult.tools().isEmpty()) {
                    mcpServers.put(serverName, new McpServerInitialized(serverName, client,
                            listToolsResult.tools()));
                    mcpMetrics.incrementInitializedServers();
                    logger.info("Successfully initialized MCP client for server: {} with {} tools", serverName, 
                            listToolsResult.tools().size());
                } else {
                    client.close();
                    mcpMetrics.decrementActiveServers();
                    noToolsClient.put(serverName, serverConfig);
                    mcpMetrics.incrementServersNoTools();
                    logger.info("MCP server {} has no tools registered", serverName);
                }
            } else {
                mcpMetrics.incrementFailedServers();
            }
        } catch (Exception e) {
            mcpMetrics.incrementFailedServers();
            logger.error("Failed to create client for server '{}': {}", serverName, e.getMessage(), e);
        }
    }

    /**
     * Loads MCP clients for a specific agent.
     *
     * @param agentName the agent name
     * @param entries the server configuration entries
     * @param absolutePath the absolute path
     */
    public void loadAgentMcpClients(String agentName, Set<Map.Entry<String, McpServer>> entries, String absolutePath) {
        Map<String, McpServerInitialized> serverClients = new HashMap<>();
        Map<String, McpServer> noTools = new HashMap<>();
        try {
            for (Map.Entry<String, McpServer> entry : entries) {
                String serverName = entry.getKey();
                McpServer serverConfig = entry.getValue();
                McpSyncClient client = createClient(serverName, serverConfig, absolutePath);
                if (client != null) {
                    mcpMetrics.incrementActiveServers();
                    McpSchema.ListToolsResult listToolsResult = client.listTools();
                    if (listToolsResult != null && listToolsResult.tools() != null && !listToolsResult
                            .tools()
                            .isEmpty()) {
                        serverClients.put(serverName, new McpServerInitialized(serverName, client,
                                listToolsResult.tools()));
                        mcpMetrics.incrementInitializedServers();
                        logger.debug("Successfully initialized agents {} MCP client for server: {} with {} tools", 
                                agentName, serverName, listToolsResult.tools().size());
                    } else {
                        client.close();
                        mcpMetrics.decrementActiveServers();
                        noTools.put(serverName, serverConfig);
                        mcpMetrics.incrementServersNoTools();
                        logger.debug("Server with name {} did not have any tools register for Agent {}", serverName,
                                agentName);
                    }
                } else {
                    noTools.put(serverName, serverConfig);
                    mcpMetrics.incrementFailedServers();
                }
            }
        } catch (Exception e) {
            mcpMetrics.incrementFailedServers();
            logger.error("Failed to create mcp client for agent servers '{}' : {}", agentName, entries, e);
        }
        agentMcpServers.put(agentName, new AgentMcpServers(agentName, serverClients, noTools));
    }

    /**
     * Creates an MCP client based on the server configuration type.
     *
     * @param serverName the name of the server
     * @param serverConfig the server configuration
     * @param fullyQualifiedName the fully qualified path name
     * @return the created MCP client, or null if creation fails
     */
    public McpSyncClient createClient(String serverName, McpServer serverConfig, String fullyQualifiedName) {
        try {
            // Validate that the server configuration is not null
            if (serverConfig == null) {
                logger.warn("Server configuration is null for server: {}", serverName);
                return null;
            }

            McpClientTransport transport = transportFactory.createTransport(serverConfig);

            if (transport == null) {
                logger.warn("Could not create transport for server: {}", serverName);
                return null;
            }
            String roots = "file://" + fullyQualifiedName;
            logger.info("FullyQualified path for Roots path {}", roots);
            
            long timeoutSeconds = qodoProperties.getMcp().getRequestTimeoutSeconds();
            logger.debug("Creating MCP client for server '{}' with request timeout of {} seconds", 
                    serverName, timeoutSeconds);
            
            // Create and configure the MCP client with roots capability enabled
            McpSyncClient client = McpClient
                    .sync(transport)
                    .roots(new McpSchema.Root(roots, "Fully Qualified file path : " + roots))
                    .requestTimeout(Duration.ofSeconds(timeoutSeconds))
                    .capabilities(McpSchema.ClientCapabilities.builder()
                            .roots(true)
                            .build())
                    .build();

            // Initialize the client connection
            client.initialize();

            return client;

        } catch (Exception e) {
            logger.error("Failed to create MCP client for server '{}': {}", serverName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Shuts down all MCP clients gracefully.
     */
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down MCP clients...");
        for (Map.Entry<String, McpServerInitialized> entry : mcpServers.entrySet()) {
            try {
                entry.getValue().client().close();
                mcpMetrics.decrementActiveServers();
                logger.info("Closed MCP client for server: {}", entry.getKey());
            } catch (Exception e) {
                logger.error("Error closing MCP client for server '{}': {}", entry.getKey(), e.getMessage(), e);
            }
        }
        for (Map.Entry<String, AgentMcpServers> entry : agentMcpServers.entrySet()) {
            entry.getValue().mcpServers().forEach((serverName, client) -> {
                try {
                    client.client().close();
                    mcpMetrics.decrementActiveServers();
                } catch (Exception e) {
                    logger.error("Error closing MCP client for agent server '{}': {}", serverName, e.getMessage(), e);
                }
            });
        }
        agentMcpServers.clear();
        noToolsClient.clear();
        mcpServers.clear();
    }
}
