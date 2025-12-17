/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Metrics collection for Kafka messaging operations.
 * Tracks message publishing, consumption, failures, and processing times.
 * Activated when messaging.provider is set to "kafka".
 */
@Component
@ConditionalOnProperty(name = "messaging.provider", havingValue = "kafka")
public class KafkaMetrics {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaMetrics.class);
    
    private final Counter messagesPublished;
    private final Counter messagesConsumed;
    private final Counter messagesFailed;
    private final Timer messageProcessingTimer;
    
    /**
     * Constructs KafkaMetrics with the given meter registry.
     * 
     * @param meterRegistry The Micrometer meter registry for metrics collection
     */
    public KafkaMetrics(MeterRegistry meterRegistry) {
        this.messagesPublished = Counter.builder("kafka.messages.published")
                .description("Total messages published to Kafka")
                .tag("provider", "kafka")
                .register(meterRegistry);
        
        this.messagesConsumed = Counter.builder("kafka.messages.consumed")
                .description("Total messages consumed from Kafka")
                .tag("provider", "kafka")
                .register(meterRegistry);
        
        this.messagesFailed = Counter.builder("kafka.messages.failed")
                .description("Total failed message processing attempts")
                .tag("provider", "kafka")
                .register(meterRegistry);
        
        this.messageProcessingTimer = Timer.builder("kafka.message.processing.time")
                .description("Time taken to process Kafka messages")
                .tag("provider", "kafka")
                .register(meterRegistry);
        
        logger.info("KafkaMetrics initialized");
    }
    
    /**
     * Records a message publication event.
     */
    public void recordMessagePublished() {
        messagesPublished.increment();
    }
    
    /**
     * Records a message consumption event.
     */
    public void recordMessageConsumed() {
        messagesConsumed.increment();
    }
    
    /**
     * Records a message processing failure.
     */
    public void recordMessageFailed() {
        messagesFailed.increment();
    }
    
    /**
     * Records the time taken to process a message.
     * 
     * @param processingTimeMs The processing time in milliseconds
     */
    public void recordProcessingTime(long processingTimeMs) {
        messageProcessingTimer.record(processingTimeMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Gets the total number of messages published.
     * 
     * @return The count
     */
    public double getMessagesPublished() {
        return messagesPublished.count();
    }
    
    /**
     * Gets the total number of messages consumed.
     * 
     * @return The count
     */
    public double getMessagesConsumed() {
        return messagesConsumed.count();
    }
    
    /**
     * Gets the total number of failed messages.
     * 
     * @return The count
     */
    public double getMessagesFailed() {
        return messagesFailed.count();
    }
}
