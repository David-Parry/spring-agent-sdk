/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.config;

import jakarta.jms.ConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.RedeliveryPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for ActiveMQ redelivery policy.
 * Defines how messages should be redelivered when transactions are rolled back.
 */
@Configuration
@ConditionalOnProperty(name = "messaging.provider", havingValue = "activemq")
public class ActiveMQRedeliveryConfig {

    /**
     * Configures the redelivery policy for ActiveMQ.
     * This policy determines how messages are redelivered when a transaction is rolled back.
     * @param messagingProperties The messaging configuration properties
     * @return The configured connection factory
     */
    @Bean(name = "connectionFactory")
    @Primary
    public ConnectionFactory connectionFactory(MessagingProperties messagingProperties) {
        MessagingProperties.ActiveMq activeMqConfig = messagingProperties.getActivemq();
        
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(activeMqConfig.getBrokerUrl());
        
        if (!activeMqConfig.getUsername().isEmpty()) {
            factory.setUserName(activeMqConfig.getUsername());
        }
        if (!activeMqConfig.getPassword().isEmpty()) {
            factory.setPassword(activeMqConfig.getPassword());
        }
        
        // Configure redelivery policy
        RedeliveryPolicy redeliveryPolicy = new RedeliveryPolicy();
        
        // Initial redelivery delay in milliseconds
        redeliveryPolicy.setInitialRedeliveryDelay(1000L);
        
        // Maximum number of redelivery attempts (set to -1 for infinite)
        redeliveryPolicy.setMaximumRedeliveries(5);
        
        // Exponential backoff multiplier
        redeliveryPolicy.setBackOffMultiplier(2.0);
        
        // Use exponential backoff
        redeliveryPolicy.setUseExponentialBackOff(true);
        
        // Maximum redelivery delay (5 minutes)
        redeliveryPolicy.setMaximumRedeliveryDelay(300000L);
        
        // Use collision avoidance (adds random factor to delay)
        redeliveryPolicy.setUseCollisionAvoidance(true);
        
        // Collision avoidance factor (15% randomization)
        redeliveryPolicy.setCollisionAvoidancePercent((short) 15);
        
        factory.setRedeliveryPolicy(redeliveryPolicy);
        
        return factory;
    }
    
    /**
     * Provides the ActiveMQConnectionFactory bean for cases where the specific type is needed.
     * This is an alias to the connectionFactory bean.
     * @param messagingProperties The messaging configuration properties
     * @return The ActiveMQ connection factory
     */
    @Bean
    public ActiveMQConnectionFactory activeMQConnectionFactory(MessagingProperties messagingProperties) {
        return (ActiveMQConnectionFactory) connectionFactory(messagingProperties);
    }
}