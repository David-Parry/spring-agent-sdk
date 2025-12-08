/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.queue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the local queue implementation.
 * These properties control the behavior of the in-memory queue system.
 */
@Component
@ConfigurationProperties(prefix = "messaging.local")
@ConditionalOnProperty(name = "messaging.provider", havingValue = "local")
public class LocalQueueProperties {
    /**
     * Creates a new LocalQueueProperties with default values.
     */
    public LocalQueueProperties() {
    }
    
    /**
     * Maximum capacity for each queue.
     * Default: 1000 messages
     */
    private int queueCapacity = 1000;
    
    /**
     * Number of consumer threads per queue.
     * Default: 1 (ensures message ordering)
     */
    private int consumerThreads = 1;
    
    /**
     * Maximum number of retry attempts for failed messages.
     * Default: 3
     */
    private int retryAttempts = 3;
    
    /**
     * Initial delay in milliseconds before first retry.
     * Default: 1000ms (1 second)
     */
    private long retryDelayMs = 1000;
    
    /**
     * Maximum delay in milliseconds between retries (with exponential backoff).
     * Default: 30000ms (30 seconds)
     */
    private long maxRetryDelayMs = 30000;
    
    /**
     * Timeout in seconds for polling messages from the queue.
     * Default: 5 seconds
     */
    private long pollTimeoutSeconds = 5;
    
    /**
     * Whether to use exponential backoff for retries.
     * Default: true
     */
    private boolean exponentialBackoff = true;
    
    /**
     * Returns the maximum capacity for each queue.
     *
     * @return the capacity in number of messages
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }
    
    /**
     * Sets the maximum capacity for each queue.
     *
     * @param queueCapacity the capacity in number of messages
     */
    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }
    
    /**
     * Returns the number of consumer threads per queue.
     *
     * @return the number of consumer threads per queue
     */
    public int getConsumerThreads() {
        return consumerThreads;
    }
    
    /**
     * Sets the number of consumer threads per queue.
     *
     * @param consumerThreads the number of consumer threads per queue
     */
    public void setConsumerThreads(int consumerThreads) {
        this.consumerThreads = consumerThreads;
    }
    
    /**
     * Returns the maximum number of retry attempts for failed messages.
     *
     * @return the number of retry attempts
     */
    public int getRetryAttempts() {
        return retryAttempts;
    }
    
    /**
     * Sets the maximum number of retry attempts for failed messages.
     *
     * @param retryAttempts the number of retry attempts
     */
    public void setRetryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
    }
    
    /**
     * Returns the initial delay in milliseconds before the first retry.
     *
     * @return the retry delay in milliseconds
     */
    public long getRetryDelayMs() {
        return retryDelayMs;
    }
    
    /**
     * Sets the initial delay in milliseconds before the first retry.
     *
     * @param retryDelayMs the retry delay in milliseconds
     */
    public void setRetryDelayMs(long retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }
    
    /**
     * Returns the maximum delay in milliseconds between retries.
     *
     * @return the maximum retry delay in milliseconds
     */
    public long getMaxRetryDelayMs() {
        return maxRetryDelayMs;
    }
    
    /**
     * Sets the maximum delay in milliseconds between retries.
     *
     * @param maxRetryDelayMs the maximum retry delay in milliseconds
     */
    public void setMaxRetryDelayMs(long maxRetryDelayMs) {
        this.maxRetryDelayMs = maxRetryDelayMs;
    }
    
    /**
     * Returns the timeout in seconds for polling messages from the queue.
     *
     * @return the poll timeout in seconds
     */
    public long getPollTimeoutSeconds() {
        return pollTimeoutSeconds;
    }
    
    /**
     * Sets the timeout in seconds for polling messages from the queue.
     *
     * @param pollTimeoutSeconds the poll timeout in seconds
     */
    public void setPollTimeoutSeconds(long pollTimeoutSeconds) {
        this.pollTimeoutSeconds = pollTimeoutSeconds;
    }
    
    /**
     * Whether to use exponential backoff for retries.
     *
     * @return true if exponential backoff is used; false otherwise
     */
    public boolean isExponentialBackoff() {
        return exponentialBackoff;
    }
    
    /**
     * Sets whether to use exponential backoff for retries.
     *
     * @param exponentialBackoff true to use exponential backoff; false otherwise
     */
    public void setExponentialBackoff(boolean exponentialBackoff) {
        this.exponentialBackoff = exponentialBackoff;
    }
}
