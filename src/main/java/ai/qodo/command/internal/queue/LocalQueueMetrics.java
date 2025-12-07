/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.queue;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collection for local queue operations.
 * Tracks queue depth, message processing rates, errors, and retries.
 */
@Component
@ConditionalOnProperty(name = "messaging.provider", havingValue = "local")
public class LocalQueueMetrics {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalQueueMetrics.class);
    
    private final LocalQueueService queueService;
    private final MeterRegistry meterRegistry;
    
    // Counters for message operations
    private final Counter messagesPublished;
    private final Counter messagesConsumed;
    private final Counter messagesFailed;
    private final Counter messagesRetried;
    private final Counter messagesDeadLettered;
    
    // Timer for message processing
    private final Timer messageProcessingTimer;
    
    // Track processing times per queue
    private final ConcurrentHashMap<String, AtomicLong> lastProcessingTimes = new ConcurrentHashMap<>();
    
    public LocalQueueMetrics(LocalQueueService queueService, MeterRegistry meterRegistry) {
        this.queueService = queueService;
        this.meterRegistry = meterRegistry;
        
        // Initialize counters
        this.messagesPublished = Counter.builder("local.queue.messages.published")
                .description("Total number of messages published to local queues")
                .tag("provider", "local")
                .register(meterRegistry);
        
        this.messagesConsumed = Counter.builder("local.queue.messages.consumed")
                .description("Total number of messages consumed from local queues")
                .tag("provider", "local")
                .register(meterRegistry);
        
        this.messagesFailed = Counter.builder("local.queue.messages.failed")
                .description("Total number of messages that failed processing")
                .tag("provider", "local")
                .register(meterRegistry);
        
        this.messagesRetried = Counter.builder("local.queue.messages.retried")
                .description("Total number of message retry attempts")
                .tag("provider", "local")
                .register(meterRegistry);
        
        this.messagesDeadLettered = Counter.builder("local.queue.messages.dead.lettered")
                .description("Total number of messages moved to dead letter queue")
                .tag("provider", "local")
                .register(meterRegistry);
        
        // Initialize timer
        this.messageProcessingTimer = Timer.builder("local.queue.message.processing.time")
                .description("Time taken to process messages")
                .tag("provider", "local")
                .register(meterRegistry);
        
        logger.info("LocalQueueMetrics initialized");
    }
    
    /**
     * Registers gauges for a specific queue to track its depth.
     * 
     * @param queueName The name of the queue
     */
    public void registerQueueGauges(String queueName) {
        // Queue size gauge
        Gauge.builder("local.queue.size", queueService, service -> service.getQueueSize(queueName))
                .description("Current number of messages in the queue")
                .tag("queue", queueName)
                .tag("provider", "local")
                .register(meterRegistry);
        
        // Remaining capacity gauge
        Gauge.builder("local.queue.capacity.remaining", queueService, 
                     service -> service.getRemainingCapacity(queueName))
                .description("Remaining capacity of the queue")
                .tag("queue", queueName)
                .tag("provider", "local")
                .register(meterRegistry);
        
        logger.debug("Registered gauges for queue: {}", queueName);
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
     * Records a message retry attempt.
     */
    public void recordMessageRetried() {
        messagesRetried.increment();
    }
    
    /**
     * Records a message being moved to dead letter queue.
     */
    public void recordMessageDeadLettered() {
        messagesDeadLettered.increment();
    }
    
    /**
     * Records the time taken to process a message.
     * 
     * @param queueName The queue the message came from
     * @param processingTimeMs The processing time in milliseconds
     */
    public void recordProcessingTime(String queueName, long processingTimeMs) {
        messageProcessingTimer.record(processingTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        lastProcessingTimes.computeIfAbsent(queueName, k -> new AtomicLong()).set(processingTimeMs);
    }
    
    /**
     * Gets the last recorded processing time for a queue.
     * 
     * @param queueName The queue name
     * @return The last processing time in milliseconds, or 0 if not recorded
     */
    public long getLastProcessingTime(String queueName) {
        AtomicLong time = lastProcessingTimes.get(queueName);
        return time != null ? time.get() : 0;
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
    
    /**
     * Gets the total number of retry attempts.
     * 
     * @return The count
     */
    public double getMessagesRetried() {
        return messagesRetried.count();
    }
    
    /**
     * Gets the total number of dead lettered messages.
     * 
     * @return The count
     */
    public double getMessagesDeadLettered() {
        return messagesDeadLettered.count();
    }
}
