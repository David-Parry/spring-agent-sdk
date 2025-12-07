/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for messaging settings.
 * Provides type-safe configuration for messaging-related settings.
 */
@Configuration
@ConfigurationProperties(prefix = "messaging")
public class MessagingProperties {
    
    private String provider = "in-memory";
    private final Queue queue = new Queue();
    private final ActiveMq activemq = new ActiveMq();
    
    public String getProvider() {
        return provider;
    }
    
    public void setProvider(String provider) {
        this.provider = provider;
    }
    
    public Queue getQueue() {
        return queue;
    }
    
    public ActiveMq getActivemq() {
        return activemq;
    }
    
    public static class Queue {
        private String event = "event";
        private String response = "response";
        private String audit = "audit";

        public String getEvent() {
            return event;
        }
        
        public void setEvent(String event) {
            this.event = event;
        }

        public String getResponse() {
            return response;
        }

        public void setResponse(String response) {
            this.response = response;
        }

        public String getAudit() {
            return audit;
        }

        public void setAudit(String audit) {
            this.audit = audit;
        }
    }
    
    public static class ActiveMq {
        private String brokerUrl = "tcp://localhost:61616";
        private String username = "CHANGEME";
        private String password = "CHANGEME";
        
        public String getBrokerUrl() {
            return brokerUrl;
        }
        
        public void setBrokerUrl(String brokerUrl) {
            this.brokerUrl = brokerUrl;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
    }
}
