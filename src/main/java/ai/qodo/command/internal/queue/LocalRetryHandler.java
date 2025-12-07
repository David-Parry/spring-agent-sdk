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
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Handles retry logic for failed messages in the local queue system.
 * Implements exponential backoff and maximum retry attempts.
 */
@Component
@ConditionalOnProperty(name = "messaging.provider", havingValue = "local")
public class LocalRetryHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalRetryHandler.class);
    
    private final LocalQueueService queueService;
    private final LocalQueueProperties properties;
    private final ScheduledExecutorService retryScheduler;
    
    // Track retry attempts per message (using message hash as key)
    private final Map<String, Integer> retryAttempts = new ConcurrentHashMap<>();
    
    public LocalRetryHandler(LocalQueueService queueService, LocalQueueProperties properties) {
        this.queueService = queueService;
        this.properties = properties;
        this.retryScheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r);
            thread.setName("local-queue-retry-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        logger.info("LocalRetryHandler initialized with max {} retry attempts", properties.getRetryAttempts());
    }
    
    /**
     * Handles a failed message by scheduling a retry or moving to dead letter queue.
     * 
     * @param queueName The queue the message came from
     * @param message The message that failed
     * @param exception The exception that caused the failure
     * @return true if message was requeued for retry, false if max retries exceeded
     */
    public boolean handleFailedMessage(String queueName, String message, Exception exception) {
        String messageKey = generateMessageKey(message);
        int currentAttempts = retryAttempts.getOrDefault(messageKey, 0);
        
        if (currentAttempts >= properties.getRetryAttempts()) {
            logger.error("Message exceeded max retry attempts ({}), moving to dead letter queue: {}", 
                        properties.getRetryAttempts(),
                        message.length() > 100 ? message.substring(0, 100) + "..." : message);
            
            // Move to dead letter queue
            moveToDeadLetterQueue(queueName, message, exception);
            
            // Clean up retry tracking
            retryAttempts.remove(messageKey);
            
            return false;
        }
        
        // Increment retry count
        int nextAttempt = currentAttempts + 1;
        retryAttempts.put(messageKey, nextAttempt);
        
        // Calculate delay with exponential backoff
        long delayMs = calculateRetryDelay(nextAttempt);
        
        logger.warn("Scheduling retry attempt {} of {} for message after {}ms delay. Error: {}", 
                   nextAttempt, 
                   properties.getRetryAttempts(), 
                   delayMs,
                   exception.getMessage());
        
        // Schedule retry
        retryScheduler.schedule(() -> {
            try {
                logger.info("Retrying message (attempt {}) for queue '{}'", nextAttempt, queueName);
                boolean requeued = queueService.enqueue(queueName, message);
                
                if (!requeued) {
                    logger.error("Failed to requeue message for retry attempt {}", nextAttempt);
                    // Try again with next retry
                    handleFailedMessage(queueName, message, 
                                      new LocalQueueException("Failed to requeue message"));
                }
            } catch (Exception e) {
                logger.error("Error during retry attempt {}", nextAttempt, e);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
        
        return true;
    }
    
    /**
     * Calculates the retry delay based on the attempt number.
     * Uses exponential backoff if enabled.
     * 
     * @param attemptNumber The current attempt number (1-based)
     * @return The delay in milliseconds
     */
    private long calculateRetryDelay(int attemptNumber) {
        if (!properties.isExponentialBackoff()) {
            return properties.getRetryDelayMs();
        }
        
        // Exponential backoff: delay * (2 ^ (attempt - 1))
        long delay = properties.getRetryDelayMs() * (long) Math.pow(2, attemptNumber - 1);
        
        // Cap at maximum delay
        return Math.min(delay, properties.getMaxRetryDelayMs());
    }
    
    /**
     * Moves a message to the dead letter queue after all retries are exhausted.
     * 
     * @param originalQueue The original queue name
     * @param message The message that failed
     * @param exception The last exception that occurred
     */
    private void moveToDeadLetterQueue(String originalQueue, String message, Exception exception) {
        String dlqName = originalQueue + ".DLQ";
        
        try {
            // Create enriched message with error information
            String dlqMessage = createDeadLetterMessage(originalQueue, message, exception);
            
            boolean enqueued = queueService.enqueue(dlqName, dlqMessage);
            
            if (enqueued) {
                logger.info("Message moved to dead letter queue: {}", dlqName);
            } else {
                logger.error("Failed to move message to dead letter queue: {}", dlqName);
            }
        } catch (Exception e) {
            logger.error("Error moving message to dead letter queue", e);
        }
    }
    
    /**
     * Creates a dead letter message with error metadata.
     * 
     * @param originalQueue The original queue name
     * @param message The original message
     * @param exception The exception that caused the failure
     * @return Enriched message for dead letter queue
     */
    private String createDeadLetterMessage(String originalQueue, String message, Exception exception) {
        // For now, just prepend error information
        // In a production system, you might want to wrap this in a JSON structure
        return String.format(
            "DEAD_LETTER|originalQueue=%s|error=%s|timestamp=%d|message=%s",
            originalQueue,
            exception.getMessage(),
            System.currentTimeMillis(),
            message
        );
    }
    
    /**
     * Generates a unique key for a message to track retry attempts.
     * Uses hash code to avoid storing full message content.
     * 
     * @param message The message
     * @return A unique key for the message
     */
    private String generateMessageKey(String message) {
        return String.valueOf(message.hashCode());
    }
    
    /**
     * Clears retry tracking for a specific message.
     * Useful for testing or manual intervention.
     * 
     * @param message The message to clear retry tracking for
     */
    public void clearRetryTracking(String message) {
        String messageKey = generateMessageKey(message);
        retryAttempts.remove(messageKey);
        logger.debug("Cleared retry tracking for message key: {}", messageKey);
    }
    
    /**
     * Gets the current retry attempt count for a message.
     * 
     * @param message The message
     * @return The number of retry attempts, or 0 if not tracked
     */
    public int getRetryAttempts(String message) {
        String messageKey = generateMessageKey(message);
        return retryAttempts.getOrDefault(messageKey, 0);
    }
    
    /**
     * Shuts down the retry scheduler.
     */
    public void shutdown() {
        logger.info("Shutting down LocalRetryHandler...");
        retryScheduler.shutdown();
        try {
            if (!retryScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                retryScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            retryScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("LocalRetryHandler shutdown complete");
    }
}
