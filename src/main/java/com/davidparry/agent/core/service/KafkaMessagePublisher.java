/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka implementation of MessagePublisher.
 * This implementation publishes messages to Kafka topics.
 * Activated when messaging.provider is set to "kafka".
 */
@Service
@ConditionalOnProperty(name = "messaging.provider", havingValue = "kafka")
public class KafkaMessagePublisher implements MessagePublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessagePublisher.class);
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    @Value("${messaging.queue.event}")
    private String eventTopic;
    
    @Value("${messaging.queue.audit}")
    private String auditTopic;
    
    @Value("${messaging.queue.response}")
    private String responseTopic;
    
    /**
     * Creates a new KafkaMessagePublisher.
     *
     * @param kafkaTemplate the Spring Kafka template used to send messages to Kafka
     */
    public KafkaMessagePublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        logger.info("KafkaMessagePublisher initialized");
    }
    
    @Override
    public void publishResponse(String message) {
        publish(responseTopic, message);
    }
    
    @Override
    public void publish(String topic, String message) {
        try {
            CompletableFuture<SendResult<String, String>> future = 
                kafkaTemplate.send(topic, message);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    logger.info("Published message to Kafka topic '{}' at partition {} offset {}: {}", 
                               topic,
                               result.getRecordMetadata().partition(),
                               result.getRecordMetadata().offset(),
                               message.length() > 200 ? message.substring(0, 200) + "..." : message);
                    logger.debug("Full message content: {}", message);
                } else {
                    logger.error("Failed to publish message to Kafka topic '{}'", topic, ex);
                }
            });
        } catch (Exception e) {
            logger.error("Failed to publish message to Kafka topic '{}'", topic, e);
            throw new RuntimeException("Failed to publish message to Kafka", e);
        }
    }
}
