/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.connection.JmsTransactionManager;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.jms.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration class for messaging infrastructure.
 * Provides beans for different messaging providers based on configuration.
 */
@Configuration
@EnableTransactionManagement
public class MessagingConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(MessagingConfig.class);
    private final MessagingProperties messagingProperties;
    
    public MessagingConfig(MessagingProperties messagingProperties) {
        this.messagingProperties = messagingProperties;
    }
    
    // ActiveMQ connection factory is now configured in ActiveMQRedeliveryConfig
    // with redelivery policy support
    
    /**
     * JMS Template for ActiveMQ.
     * Only created when messaging provider is set to "activemq".
     */
    @Bean
    @ConditionalOnProperty(name = "messaging.provider", havingValue = "activemq")
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        // Configure for topics instead of queues
        template.setPubSubDomain(false);
        // Enable session transacted mode for transaction support
        template.setSessionTransacted(true);
        return template;
    }
    
    /**
     * JMS Transaction Manager for managing JMS transactions.
     * This enables @Transactional support for JMS operations.
     * Only created when messaging provider is set to "activemq".
     */
    @Bean
    @ConditionalOnProperty(name = "messaging.provider", havingValue = "activemq")
    public PlatformTransactionManager jmsTransactionManager(ConnectionFactory connectionFactory) {
        return new JmsTransactionManager(connectionFactory);
    }
    
    /**
     * JMS Listener Container Factory for ActiveMQ message consumers.
     * Only created when messaging provider is set to "activemq".
     */
    @Bean
    @ConditionalOnProperty(name = "messaging.provider", havingValue = "activemq")
    public JmsListenerContainerFactory<DefaultMessageListenerContainer> jmsListenerContainerFactory(
            ConnectionFactory connectionFactory, PlatformTransactionManager jmsTransactionManager) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        // Configure for topics instead of queues
        factory.setPubSubDomain(false);
        // CRITICAL: Set concurrency to exactly 1
        // This ensures only one consumer thread per container
        factory.setConcurrency("1");
        
        // Enable transaction support
        factory.setSessionTransacted(true);
        factory.setTransactionManager(jmsTransactionManager);
        
        // Use CLIENT_ACKNOWLEDGE or SESSION_TRANSACTED for transaction support
        // Messages will only be acknowledged after successful transaction commit
        factory.setSessionAcknowledgeMode(jakarta.jms.Session.SESSION_TRANSACTED);
        
        // Configure error handling for automatic rollback and redelivery
        factory.setErrorHandler(t -> {
            // Log the error but let the transaction manager handle rollback
            // The message will be redelivered based on ActiveMQ redelivery policy
            logger.error("Error processing JMS message, transaction will be rolled back", t);
        });
        
        return factory;
    }
}