/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * Kafka message consumer that listens to messages from configured Kafka topics
 * and routes them to appropriate services using the MessageRouter.
 * This implementation is activated when messaging.provider is set to "kafka".
 * 
 * Uses manual acknowledgment mode - messages are only acknowledged after successful processing.
 * Failed messages will be redelivered based on Kafka consumer configuration.
 */
@Service
@ConditionalOnProperty(name = "messaging.provider", havingValue = "kafka")
public class KafkaMessageConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageConsumer.class);
    
    private final ApplicationContext applicationContext;
    
    /**
     * Constructs a KafkaMessageConsumer with the given application context.
     * 
     * @param applicationContext The Spring application context for bean lookup
     */
    public KafkaMessageConsumer(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        logger.info("KafkaMessageConsumer initialized");
    }
    
    /**
     * Listens for event messages from the configured Kafka event topic.
     * 
     * @param record The Kafka consumer record containing the message
     * @param acknowledgment The acknowledgment handle for manual commit
     */
    @KafkaListener(
        topics = "${messaging.queue.event}",
        groupId = "${messaging.kafka.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onEventMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        process(record, acknowledgment);
    }
    
    /**
     * Listens for response messages from the configured Kafka response topic.
     * 
     * @param record The Kafka consumer record containing the message
     * @param acknowledgment The acknowledgment handle for manual commit
     */
    @KafkaListener(
        topics = "${messaging.queue.response}",
        groupId = "${messaging.kafka.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onResponseMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        process(record, acknowledgment);
    }
    
    /**
     * Processes a message by routing it through the MessageRouter.
     * Acknowledges the message only after successful processing.
     * 
     * @param record The Kafka consumer record containing the message
     * @param acknowledgment The acknowledgment handle for manual commit
     */
    protected void process(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String message = record.value();
        logger.info("Received message from Kafka topic '{}' partition {} offset {}: {}", 
                   record.topic(),
                   record.partition(),
                   record.offset(),
                   message.length() > 200 ? message.substring(0, 200) + "..." : message);
        logger.debug("Full message content: {}", message);
        
        try {
            MessageRouter router = applicationContext.getBean(MessageRouter.class);
            router.processMessage(message);
            acknowledgment.acknowledge();
            logger.debug("Successfully processed and acknowledged message from topic '{}'", record.topic());
        } catch (Exception e) {
            logger.error("Error processing message from Kafka topic '{}' partition {} offset {}: {}", 
                        record.topic(), 
                        record.partition(),
                        record.offset(),
                        e.getMessage(), 
                        e);
            // Don't acknowledge - message will be redelivered based on consumer configuration
            throw e;
        }
    }
}
