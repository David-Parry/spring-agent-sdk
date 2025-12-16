/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.config;

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
    private final Kafka kafka = new Kafka();
    
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
    
    public Kafka getKafka() {
        return kafka;
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
    
    public static class Kafka {
        // Connection settings
        private String bootstrapServers = "localhost:9092";
        private String groupId = "qodo-agent-group";
        
        // Consumer settings
        private String autoOffsetReset = "earliest";
        private boolean enableAutoCommit = false;
        private int maxPollRecords = 500;
        private int sessionTimeoutMs = 30000;
        private int heartbeatIntervalMs = 10000;
        private int requestTimeoutMs = 30000;
        
        // Producer settings
        private int retries = 3;
        private String acks = "all";
        private int lingerMs = 1;
        private int batchSize = 16384;
        private int bufferMemory = 33554432;
        
        // SSL/SASL settings (disabled by default)
        private final Security security = new Security();
        
        public String getBootstrapServers() {
            return bootstrapServers;
        }
        
        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }
        
        public String getGroupId() {
            return groupId;
        }
        
        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }
        
        public String getAutoOffsetReset() {
            return autoOffsetReset;
        }
        
        public void setAutoOffsetReset(String autoOffsetReset) {
            this.autoOffsetReset = autoOffsetReset;
        }
        
        public boolean isEnableAutoCommit() {
            return enableAutoCommit;
        }
        
        public void setEnableAutoCommit(boolean enableAutoCommit) {
            this.enableAutoCommit = enableAutoCommit;
        }
        
        public int getMaxPollRecords() {
            return maxPollRecords;
        }
        
        public void setMaxPollRecords(int maxPollRecords) {
            this.maxPollRecords = maxPollRecords;
        }
        
        public int getSessionTimeoutMs() {
            return sessionTimeoutMs;
        }
        
        public void setSessionTimeoutMs(int sessionTimeoutMs) {
            this.sessionTimeoutMs = sessionTimeoutMs;
        }
        
        public int getHeartbeatIntervalMs() {
            return heartbeatIntervalMs;
        }
        
        public void setHeartbeatIntervalMs(int heartbeatIntervalMs) {
            this.heartbeatIntervalMs = heartbeatIntervalMs;
        }
        
        public int getRequestTimeoutMs() {
            return requestTimeoutMs;
        }
        
        public void setRequestTimeoutMs(int requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
        }
        
        public int getRetries() {
            return retries;
        }
        
        public void setRetries(int retries) {
            this.retries = retries;
        }
        
        public String getAcks() {
            return acks;
        }
        
        public void setAcks(String acks) {
            this.acks = acks;
        }
        
        public int getLingerMs() {
            return lingerMs;
        }
        
        public void setLingerMs(int lingerMs) {
            this.lingerMs = lingerMs;
        }
        
        public int getBatchSize() {
            return batchSize;
        }
        
        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
        
        public int getBufferMemory() {
            return bufferMemory;
        }
        
        public void setBufferMemory(int bufferMemory) {
            this.bufferMemory = bufferMemory;
        }
        
        public Security getSecurity() {
            return security;
        }
        
        public static class Security {
            private boolean enabled = false;
            private String protocol = "PLAINTEXT"; // PLAINTEXT, SSL, SASL_PLAINTEXT, SASL_SSL
            
            // SSL settings
            private String truststoreLocation;
            private String truststorePassword;
            private String keystoreLocation;
            private String keystorePassword;
            private String keyPassword;
            
            // SASL settings
            private String saslMechanism = "PLAIN"; // PLAIN, SCRAM-SHA-256, SCRAM-SHA-512, OAUTHBEARER
            private String saslJaasConfig;
            
            public boolean isEnabled() {
                return enabled;
            }
            
            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
            
            public String getProtocol() {
                return protocol;
            }
            
            public void setProtocol(String protocol) {
                this.protocol = protocol;
            }
            
            public String getTruststoreLocation() {
                return truststoreLocation;
            }
            
            public void setTruststoreLocation(String truststoreLocation) {
                this.truststoreLocation = truststoreLocation;
            }
            
            public String getTruststorePassword() {
                return truststorePassword;
            }
            
            public void setTruststorePassword(String truststorePassword) {
                this.truststorePassword = truststorePassword;
            }
            
            public String getKeystoreLocation() {
                return keystoreLocation;
            }
            
            public void setKeystoreLocation(String keystoreLocation) {
                this.keystoreLocation = keystoreLocation;
            }
            
            public String getKeystorePassword() {
                return keystorePassword;
            }
            
            public void setKeystorePassword(String keystorePassword) {
                this.keystorePassword = keystorePassword;
            }
            
            public String getKeyPassword() {
                return keyPassword;
            }
            
            public void setKeyPassword(String keyPassword) {
                this.keyPassword = keyPassword;
            }
            
            public String getSaslMechanism() {
                return saslMechanism;
            }
            
            public void setSaslMechanism(String saslMechanism) {
                this.saslMechanism = saslMechanism;
            }
            
            public String getSaslJaasConfig() {
                return saslJaasConfig;
            }
            
            public void setSaslJaasConfig(String saslJaasConfig) {
                this.saslJaasConfig = saslJaasConfig;
            }
        }
    }
}
