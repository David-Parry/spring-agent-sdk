/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.queue;

import ai.qodo.command.internal.config.MessagingProperties;
import ai.qodo.command.internal.service.MessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Local queue implementation of MessagePublisher.
 * Publishes messages to in-memory queues with thread separation from consumers.
 * 
 * This implementation is activated when messaging.provider is set to "local".
 */
@Service
@ConditionalOnProperty(name = "messaging.provider", havingValue = "local")
public class LocalMessagePublisher implements MessagePublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalMessagePublisher.class);
    
    private final LocalQueueService queueService;
    private final MessagingProperties messagingProperties;
    
    public LocalMessagePublisher(LocalQueueService queueService, MessagingProperties messagingProperties) {
        this.queueService = queueService;
        this.messagingProperties = messagingProperties;
        logger.info("LocalMessagePublisher initialized");
    }

    @Override
    public void publishResponse(String message) {
        publish(messagingProperties.getQueue().getResponse(), message);
    }
    
    @Override
    public void publish(String topic, String message) {
        // Publish asynchronously to ensure thread separation from consumer
        CompletableFuture.runAsync(() -> {
            try {
                boolean success = queueService.enqueue(topic, message);
                
                if (success) {
                    logger.debug("Message published to local queue '{}': {}", 
                                topic, 
                                message.length() > 100 ? message.substring(0, 100) + "..." : message);
                } else {
                    logger.error("Failed to publish message to local queue '{}' - queue may be full", topic);
                    throw new LocalQueueException("Failed to enqueue message to queue: " + topic);
                }
            } catch (Exception e) {
                logger.error("Error publishing message to local queue '{}'", topic, e);
                throw new LocalQueueException("Error publishing message to queue: " + topic, e);
            }
        }).exceptionally(throwable -> {
            logger.error("Async publish failed for queue '{}'", topic, throwable);
            return null;
        });
        
        logger.trace("Message publish initiated for queue '{}'", topic);
    }
}
