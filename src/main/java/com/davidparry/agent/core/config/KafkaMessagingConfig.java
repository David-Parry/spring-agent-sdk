/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.config;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for Kafka messaging infrastructure.
 * Provides beans for Kafka producer, consumer, and admin when messaging.provider is set to "kafka".
 */
@Configuration
@ConditionalOnProperty(name = "messaging.provider", havingValue = "kafka")
public class KafkaMessagingConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessagingConfig.class);
    
    private final MessagingProperties messagingProperties;
    
    public KafkaMessagingConfig(MessagingProperties messagingProperties) {
        this.messagingProperties = messagingProperties;
        logger.info("KafkaMessagingConfig initialized - using Kafka messaging");
    }
    
    /**
     * Applies security configuration (SSL/SASL) to the provided config map.
     * Only applies settings if security is enabled.
     */
    private void applySecurityConfig(Map<String, Object> configProps) {
        MessagingProperties.Kafka.Security security = messagingProperties.getKafka().getSecurity();
        
        if (!security.isEnabled()) {
            logger.debug("Kafka security is disabled, using PLAINTEXT");
            return;
        }
        
        logger.info("Kafka security is enabled with protocol: {}", security.getProtocol());
        
        // Set security protocol
        configProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, security.getProtocol());
        
        // SSL configuration
        if (security.getProtocol().contains("SSL")) {
            if (security.getTruststoreLocation() != null && !security.getTruststoreLocation().isEmpty()) {
                configProps.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, security.getTruststoreLocation());
                configProps.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, security.getTruststorePassword());
            }
            
            if (security.getKeystoreLocation() != null && !security.getKeystoreLocation().isEmpty()) {
                configProps.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, security.getKeystoreLocation());
                configProps.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, security.getKeystorePassword());
                configProps.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, security.getKeyPassword());
            }
            
            logger.debug("SSL configuration applied");
        }
        
        // SASL configuration
        if (security.getProtocol().contains("SASL")) {
            configProps.put(SaslConfigs.SASL_MECHANISM, security.getSaslMechanism());
            
            if (security.getSaslJaasConfig() != null && !security.getSaslJaasConfig().isEmpty()) {
                configProps.put(SaslConfigs.SASL_JAAS_CONFIG, security.getSaslJaasConfig());
            }
            
            logger.debug("SASL configuration applied with mechanism: {}", security.getSaslMechanism());
        }
    }
    
    /**
     * Creates the Kafka producer factory with configured settings.
     * 
     * @return ProducerFactory for String key-value pairs
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        MessagingProperties.Kafka kafka = messagingProperties.getKafka();
        
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, kafka.getAcks());
        configProps.put(ProducerConfig.RETRIES_CONFIG, kafka.getRetries());
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, kafka.getLingerMs());
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, kafka.getBatchSize());
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, kafka.getBufferMemory());
        
        // Apply security configuration
        applySecurityConfig(configProps);
        
        logger.info("Kafka ProducerFactory created with bootstrap servers: {}", kafka.getBootstrapServers());
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    /**
     * Creates the KafkaTemplate for sending messages.
     * 
     * @return KafkaTemplate for String key-value pairs
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
    
    /**
     * Creates the Kafka consumer factory with configured settings.
     * 
     * @return ConsumerFactory for String key-value pairs
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        MessagingProperties.Kafka kafka = messagingProperties.getKafka();
        
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, kafka.getGroupId());
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafka.getAutoOffsetReset());
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, kafka.isEnableAutoCommit());
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, kafka.getMaxPollRecords());
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, kafka.getSessionTimeoutMs());
        configProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, kafka.getHeartbeatIntervalMs());
        configProps.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, kafka.getRequestTimeoutMs());
        
        // Apply security configuration
        applySecurityConfig(configProps);
        
        logger.info("Kafka ConsumerFactory created with group ID: {}", kafka.getGroupId());
        return new DefaultKafkaConsumerFactory<>(configProps);
    }
    
    /**
     * Creates the Kafka listener container factory for @KafkaListener annotations.
     * Configured with manual acknowledgment mode and single concurrency for message ordering.
     * 
     * @return ConcurrentKafkaListenerContainerFactory for String key-value pairs
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(1); // Single consumer for message ordering
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        logger.info("Kafka listener container factory created with concurrency: 1");
        return factory;
    }
    
    /**
     * Creates the Kafka admin client for health checks and topic management.
     * 
     * @return KafkaAdmin instance
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, 
                    messagingProperties.getKafka().getBootstrapServers());
        
        // Apply security configuration for admin client
        applySecurityConfig(configs);
        
        return new KafkaAdmin(configs);
    }
}
