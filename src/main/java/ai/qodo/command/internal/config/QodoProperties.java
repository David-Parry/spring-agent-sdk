/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Qodo application settings.
 * Provides type-safe configuration for Qodo-related settings.
 */
@Configuration
@ConfigurationProperties(prefix = "qodo")
public class QodoProperties {
    
    private String baseUrl = "https://api.command.qodo.ai";
    private String blockedTools = "";
    private final Websocket websocket = new Websocket();
    private final Mcp mcp = new Mcp();
    
    public String getBaseUrl() {
        return baseUrl;
    }
    
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    public String getBlockedTools() {
        return blockedTools;
    }
    
    public void setBlockedTools(String blockedTools) {
        this.blockedTools = blockedTools;
    }
    
    public Websocket getWebsocket() {
        return websocket;
    }
    
    public Mcp getMcp() {
        return mcp;
    }
    
    public static class Mcp {
        private long requestTimeoutSeconds = 90;
        
        public long getRequestTimeoutSeconds() {
            return requestTimeoutSeconds;
        }
        
        public void setRequestTimeoutSeconds(long requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
        }
    }
    
    public static class Websocket {
        private String token = "";
        private long pingIntervalSeconds = 30;
        private long pongTimeoutSeconds = 10;
        private long connectionTimeoutSeconds = 60;
        private long readySignalTimeoutSeconds = 30;
        private int maxReconnectAttempts = 3;
        private Duration initialReconnectDelay = Duration.ofSeconds(1);
        private Duration maxReconnectDelay = Duration.ofSeconds(10);

        public String getToken() {
            return token;
        }
        
        public void setToken(String token) {
            this.token = token;
        }

        public long getPingIntervalSeconds() {
            return pingIntervalSeconds;
        }

        public void setPingIntervalSeconds(long pingIntervalSeconds) {
            this.pingIntervalSeconds = pingIntervalSeconds;
        }

        public long getPongTimeoutSeconds() {
            return pongTimeoutSeconds;
        }

        public void setPongTimeoutSeconds(long pongTimeoutSeconds) {
            this.pongTimeoutSeconds = pongTimeoutSeconds;
        }

        public long getConnectionTimeoutSeconds() {
            return connectionTimeoutSeconds;
        }

        public void setConnectionTimeoutSeconds(long connectionTimeoutSeconds) {
            this.connectionTimeoutSeconds = connectionTimeoutSeconds;
        }

        public int getMaxReconnectAttempts() {
            return maxReconnectAttempts;
        }

        public void setMaxReconnectAttempts(int maxReconnectAttempts) {
            this.maxReconnectAttempts = maxReconnectAttempts;
        }

        public Duration getInitialReconnectDelay() {
            return initialReconnectDelay;
        }

        public void setInitialReconnectDelay(Duration initialReconnectDelay) {
            this.initialReconnectDelay = initialReconnectDelay;
        }

        public Duration getMaxReconnectDelay() {
            return maxReconnectDelay;
        }

        public void setMaxReconnectDelay(Duration maxReconnectDelay) {
            this.maxReconnectDelay = maxReconnectDelay;
        }

        public long getReadySignalTimeoutSeconds() {
            return readySignalTimeoutSeconds;
        }

        public void setReadySignalTimeoutSeconds(long readySignalTimeoutSeconds) {
            this.readySignalTimeoutSeconds = readySignalTimeoutSeconds;
        }
    }

}
