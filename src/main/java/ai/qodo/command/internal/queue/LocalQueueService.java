/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Local in-memory queue service that provides thread-safe queue operations.
 * This service manages multiple named queues and provides methods for
 * enqueueing and dequeueing messages with proper thread separation.
 * 
 * Activated when messaging.provider is set to "local".
 */
@Service
@ConditionalOnProperty(name = "messaging.provider", havingValue = "local")
public class LocalQueueService {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalQueueService.class);
    
    private final Map<String, BlockingQueue<String>> queues = new ConcurrentHashMap<>();
    private final LocalQueueProperties properties;
    private volatile boolean shutdown = false;
    
    public LocalQueueService(LocalQueueProperties properties) {
        this.properties = properties;
        logger.info("LocalQueueService initialized with capacity: {}", properties.getQueueCapacity());
    }
    
    /**
     * Gets or creates a queue with the specified name.
     * 
     * @param queueName The name of the queue
     * @return The BlockingQueue instance
     */
    public BlockingQueue<String> getQueue(String queueName) {
        return queues.computeIfAbsent(queueName, name -> {
            logger.info("Creating new local queue: {} with capacity: {}", name, properties.getQueueCapacity());
            return new LinkedBlockingQueue<>(properties.getQueueCapacity());
        });
    }
    
    /**
     * Enqueues a message to the specified queue.
     * This operation is non-blocking and returns immediately.
     * 
     * @param queueName The name of the queue
     * @param message The message to enqueue
     * @return true if the message was successfully enqueued, false if queue is full
     */
    public boolean enqueue(String queueName, String message) {
        if (shutdown) {
            logger.warn("Cannot enqueue message - service is shutting down");
            return false;
        }
        
        try {
            BlockingQueue<String> queue = getQueue(queueName);
            boolean offered = queue.offer(message, 1, TimeUnit.SECONDS);
            
            if (offered) {
                logger.debug("Message enqueued to queue '{}'. Current size: {}", queueName, queue.size());
            } else {
                logger.warn("Failed to enqueue message to queue '{}' - queue may be full (capacity: {})", 
                           queueName, properties.getQueueCapacity());
            }
            
            return offered;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while enqueueing message to queue '{}'", queueName, e);
            return false;
        }
    }
    
    /**
     * Dequeues a message from the specified queue.
     * This operation blocks until a message is available or timeout occurs.
     * 
     * @param queueName The name of the queue
     * @param timeout The maximum time to wait
     * @param unit The time unit of the timeout
     * @return The dequeued message, or null if timeout occurs
     * @throws InterruptedException if interrupted while waiting
     */
    public String dequeue(String queueName, long timeout, TimeUnit unit) throws InterruptedException {
        if (shutdown) {
            logger.debug("Service is shutting down, returning null from dequeue");
            return null;
        }
        
        BlockingQueue<String> queue = getQueue(queueName);
        String message = queue.poll(timeout, unit);
        
        if (message != null) {
            logger.debug("Message dequeued from queue '{}'. Remaining size: {}", queueName, queue.size());
        }
        
        return message;
    }
    
    /**
     * Gets the current size of the specified queue.
     * 
     * @param queueName The name of the queue
     * @return The number of messages in the queue
     */
    public int getQueueSize(String queueName) {
        BlockingQueue<String> queue = queues.get(queueName);
        return queue != null ? queue.size() : 0;
    }
    
    /**
     * Gets the remaining capacity of the specified queue.
     * 
     * @param queueName The name of the queue
     * @return The remaining capacity
     */
    public int getRemainingCapacity(String queueName) {
        BlockingQueue<String> queue = queues.get(queueName);
        return queue != null ? queue.remainingCapacity() : properties.getQueueCapacity();
    }
    
    /**
     * Checks if the service is shutting down.
     * 
     * @return true if shutdown is in progress
     */
    public boolean isShutdown() {
        return shutdown;
    }
    
    /**
     * Clears all messages from the specified queue.
     * 
     * @param queueName The name of the queue to clear
     */
    public void clearQueue(String queueName) {
        BlockingQueue<String> queue = queues.get(queueName);
        if (queue != null) {
            int cleared = queue.size();
            queue.clear();
            logger.info("Cleared {} messages from queue '{}'", cleared, queueName);
        }
    }
    
    /**
     * Gets all queue names currently managed by this service.
     * 
     * @return Set of queue names
     */
    public java.util.Set<String> getQueueNames() {
        return queues.keySet();
    }
    
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down LocalQueueService...");
        shutdown = true;
        
        // Log queue states before shutdown
        queues.forEach((name, queue) -> {
            int size = queue.size();
            if (size > 0) {
                logger.warn("Queue '{}' has {} unprocessed messages at shutdown", name, size);
            }
        });
        
        logger.info("LocalQueueService shutdown complete");
    }
}
