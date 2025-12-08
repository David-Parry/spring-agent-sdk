/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.service;

import com.davidparry.agent.core.service.MessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

/**
 * Apache ActiveMQ implementation of MessagePublisher.
 * This implementation publishes messages to ActiveMQ topics.
 * Activated when messaging.provider is set to "activemq".
 */
@Service
@ConditionalOnProperty(name = "messaging.provider", havingValue = "activemq")
public class ActiveMQMessagePublisher implements MessagePublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(ActiveMQMessagePublisher.class);
    
    private final JmsTemplate jmsTemplate;

    @Value("${messaging.queue.event}")
    private String eventQueue;

    @Value("${messaging.queue.audit}")
    private String auditQueue;

    @Value("${messaging.queue.response}")
    private String responseQueue;

    @Override
    public void publishResponse(String message) {
        publish(responseQueue, message);
    }

    /**
     * Creates a new ActiveMQMessagePublisher.
     *
     * @param jmsTemplate the Spring JMS template used to send messages to ActiveMQ
     */
    public ActiveMQMessagePublisher(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    @Override
    public void publish(String queue, String message) {
        try {
            jmsTemplate.convertAndSend(queue, message);
            logger.info("Published message to ActiveMQ queue '{}': {}", queue,
                       message.length() > 200 ? message.substring(0, 200) + "..." : message);
            logger.debug("Full message content: {}", message);
        } catch (Exception e) {
            logger.error("Failed to publish message to ActiveMQ queue '{}'", queue, e);
            throw new RuntimeException("Failed to publish message", e);
        }
    }
}