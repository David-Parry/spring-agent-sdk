/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Calculates queue capacity dynamically based on available JVM heap memory.
 * This component monitors JVM memory and adjusts queue capacity to prevent
 * OutOfMemoryError while maximizing throughput.
 */
@Component
public class AdaptiveQueueCapacity {
    
    private static final Logger logger = LoggerFactory.getLogger(AdaptiveQueueCapacity.class);
    
    /**
     * Percentage of max heap that can be used for queue storage.
     * Default: 0.3 (30% of max heap)
     */
    private static final double HEAP_UTILIZATION_THRESHOLD = 0.3;
    
    /**
     * Minimum queue capacity regardless of available memory.
     * Ensures basic functionality even under memory pressure.
     */
    private static final int MIN_CAPACITY = 100;
    
    /**
     * Maximum queue capacity to prevent excessive memory usage.
     * Acts as a safety ceiling.
     */
    private static final int MAX_CAPACITY = 100_000;
    
    /**
     * Estimated average message size in bytes.
     * Used to calculate how many messages can fit in available memory.
     * Default: 2KB per message (conservative estimate for JSON messages)
     */
    private static final long ESTIMATED_MESSAGE_SIZE_BYTES = 2048;
    
    /**
     * Number of queues expected to be active simultaneously.
     * Used to divide available memory among queues.
     */
    private static final int EXPECTED_QUEUE_COUNT = 3; // event, response, DLQ
    
    public AdaptiveQueueCapacity() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        int calculatedCapacity = calculateCapacity();
        
        logger.info("AdaptiveQueueCapacity initialized:");
        logger.info("  Max JVM Heap: {} MB", maxMemory / (1024 * 1024));
        logger.info("  Heap Threshold: {}%", HEAP_UTILIZATION_THRESHOLD * 100);
        logger.info("  Estimated Message Size: {} bytes", ESTIMATED_MESSAGE_SIZE_BYTES);
        logger.info("  Expected Queue Count: {}", EXPECTED_QUEUE_COUNT);
        logger.info("  Calculated Capacity per Queue: {}", calculatedCapacity);
        logger.info("  Capacity Range: {} - {}", MIN_CAPACITY, MAX_CAPACITY);
    }
    
    /**
     * Calculates the optimal queue capacity based on current JVM memory state.
     * 
     * Algorithm:
     * 1. Get max heap size from JVM
     * 2. Calculate available memory for queues (max heap * threshold)
     * 3. Divide by expected queue count
     * 4. Divide by estimated message size
     * 5. Clamp between MIN and MAX capacity
     * 
     * @return The calculated queue capacity
     */
    public int calculateCapacity() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        
        // Calculate memory available for all queues
        long availableForQueues = (long)(maxMemory * HEAP_UTILIZATION_THRESHOLD);
        
        // Divide among expected queues
        long perQueueMemory = availableForQueues / EXPECTED_QUEUE_COUNT;
        
        // Calculate capacity based on message size
        int calculatedCapacity = (int)(perQueueMemory / ESTIMATED_MESSAGE_SIZE_BYTES);
        
        // Clamp to safe range
        int finalCapacity = Math.max(MIN_CAPACITY, Math.min(calculatedCapacity, MAX_CAPACITY));
        
        logger.debug("Capacity calculation: maxMemory={}MB, available={}MB, perQueue={}MB, capacity={}", 
                    maxMemory / (1024 * 1024),
                    availableForQueues / (1024 * 1024),
                    perQueueMemory / (1024 * 1024),
                    finalCapacity);
        
        return finalCapacity;
    }
    
    /**
     * Gets the current available memory in bytes.
     * Useful for monitoring and diagnostics.
     * 
     * @return Available memory in bytes
     */
    public long getAvailableMemory() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        return maxMemory - usedMemory;
    }
    
    /**
     * Gets the current heap utilization as a percentage.
     * 
     * @return Heap utilization (0.0 to 1.0)
     */
    public double getCurrentHeapUtilization() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        return (double) usedMemory / maxMemory;
    }
    
    /**
     * Checks if the system is under memory pressure.
     * 
     * @return true if heap utilization exceeds 80%
     */
    public boolean isMemoryPressureHigh() {
        return getCurrentHeapUtilization() > 0.8;
    }
    
    /**
     * Gets the minimum capacity.
     * 
     * @return Minimum capacity
     */
    public int getMinCapacity() {
        return MIN_CAPACITY;
    }
    
    /**
     * Gets the maximum capacity.
     * 
     * @return Maximum capacity
     */
    public int getMaxCapacity() {
        return MAX_CAPACITY;
    }
    
    /**
     * Gets the heap utilization threshold.
     * 
     * @return Heap utilization threshold (0.0 to 1.0)
     */
    public double getHeapUtilizationThreshold() {
        return HEAP_UTILIZATION_THRESHOLD;
    }
}
