/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.config.transport;

import ai.qodo.command.internal.mcp.CommandServer;
import ai.qodo.command.internal.mcp.HttpServer;
import ai.qodo.command.internal.mcp.McpServer;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Default implementation of TransportFactory that creates appropriate transports
 * based on server configuration type (STDIO, HTTP, Streamable HTTP).
 */
@Component
public class DefaultTransportFactory implements TransportFactory {
    private static final Logger logger = LoggerFactory.getLogger(DefaultTransportFactory.class);

    @Override
    public McpClientTransport createTransport(McpServer serverConfig) {
        if (serverConfig instanceof CommandServer commandServer) {
            return createStdioTransport(commandServer);
        } else if (serverConfig instanceof HttpServer httpServer) {
            if (HttpServer.STREAMABLE_HTTP_TYPE.equalsIgnoreCase(httpServer.type())) {
                return createStreamableHttpTransport(httpServer);
            } else {
                return createHttpTransport(httpServer);
            }
        } else {
            logger.error("Unknown server configuration type: {}", serverConfig.getClass().getName());
            return null;
        }
    }

    /**
     * Creates a STDIO transport for command-based servers.
     *
     * @param commandServer the command server configuration
     * @return the STDIO transport
     */
    private StdioClientTransport createStdioTransport(CommandServer commandServer) {
        try {
            // Validate that the command is null or empty
            if (commandServer.command() == null || commandServer.command().trim().isEmpty()) {
                logger.warn("Command is null or empty, cannot create STDIO transport");
                return null;
            }

            // Check if the command exists and is executable
            if (!isCommandAvailable(commandServer.command())) {
                logger.warn("Command '{}' is not available or not executable, cannot create STDIO transport",
                        commandServer.command());
                return null;
            }

            ServerParameters.Builder builder = ServerParameters.builder(commandServer.command());

            if (commandServer.args() != null && !commandServer.args().isEmpty()) {
                builder.args(commandServer.args().toArray(new String[0]));
            }

            if (commandServer.env() != null && !commandServer.env().isEmpty()) {
                builder.env(commandServer.env());
            }

            ServerParameters params = builder.build();
            return new StdioClientTransport(params);

        } catch (Exception e) {
            logger.error("Failed to create STDIO transport: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Checks if a command is available and executable on the system.
     *
     * @param command the command to check
     * @return true if the command is available, false otherwise
     */
    private boolean isCommandAvailable(String command) {
        try {
            // Try to execute the command with --version or --help to see if it exists
            // Use a simple approach that works across different operating systems
            ProcessBuilder pb = new ProcessBuilder();

            // For Unix-like systems, use 'which' command
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                // Windows: use 'where' command
                pb.command("where", command);
            } else {
                // Unix-like: use 'which' command
                pb.command("which", command);
            }

            Process process = pb.start();
            int exitCode = process.waitFor();

            // If exit code is 0, the command exists
            return exitCode == 0;

        } catch (Exception e) {
            // If any exception occurs, assume the command is not available
            logger.debug("Error checking command availability for '{}': {}", command, e.getMessage());
            return false;
        }
    }

    /**
     * Creates an HTTP transport for HTTP-based servers.
     * Uses the HttpClientSseClientTransport for Server-Sent Events communication.
     *
     * @param httpServer the HTTP server configuration
     * @return the HTTP transport
     */
    private McpClientTransport createHttpTransport(HttpServer httpServer) {
        logger.info("Creating HTTP SSE transport for URL: {}", httpServer.url());
        try {
            HttpClientSseClientTransport.Builder builder = HttpClientSseClientTransport.builder(httpServer.url());
            if (httpServer.headers() != null && !httpServer.headers().isEmpty()) {
                builder.customizeRequest(rb -> httpServer.headers().forEach(rb::header));
            }
            return builder.build();
        } catch (Exception e) {
            logger.error("Failed to create HTTP SSE transport: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Creates a Streamable HTTP transport for HTTP-based servers.
     *
     * @param httpServer the HTTP server configuration
     * @return the Streamable HTTP transport
     */
    private McpClientTransport createStreamableHttpTransport(HttpServer httpServer) {
        logger.info("Creating HTTP Streamable transport for URL: {}", httpServer.url());
        try {
            WebClient.Builder webClientBuilder = WebClient.builder().baseUrl(httpServer.url());
            if (httpServer.headers() != null && !httpServer.headers().isEmpty()) {
                webClientBuilder.defaultHeaders(headers -> httpServer.headers().forEach(headers::add));
            }
            WebClientStreamableHttpTransport.Builder builder =
                    WebClientStreamableHttpTransport.builder(webClientBuilder);
            return builder.build();
        } catch (Exception e) {
            logger.error("Failed to create HTTP Streamable transport: {}", e.getMessage(), e);
            return null;
        }
    }
}
