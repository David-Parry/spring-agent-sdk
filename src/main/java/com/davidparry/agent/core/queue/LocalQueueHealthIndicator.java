/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.queue;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Health indicator for the local queue system.
 * Reports the health status of local queues including queue sizes and capacity.
 */
@Component
public class LocalQueueHealthIndicator implements HealthIndicator {
    
    private final LocalQueueService queueService;
    private final LocalMessageConsumer messageConsumer;
    
    public LocalQueueHealthIndicator(
            LocalQueueService queueService,
            LocalMessageConsumer messageConsumer) {
        this.queueService = queueService;
        this.messageConsumer = messageConsumer;
    }
    
    @Override
    public Health health() {
        try {
            Map<String, Object> details = new HashMap<>();
            
            // Check if consumer is running
            boolean consumerRunning = messageConsumer.isRunning();
            details.put("consumerRunning", consumerRunning);
            
            // Check if service is shutting down
            boolean shutdown = queueService.isShutdown();
            details.put("shutdown", shutdown);
            
            // Get queue statistics
            Map<String, Map<String, Object>> queueStats = new HashMap<>();
            int capacity = queueService.getQueueCapacity();
            
            for (String queueName : queueService.getQueueNames()) {
                Map<String, Object> stats = new HashMap<>();
                int size = queueService.getQueueSize(queueName);
                int remaining = queueService.getRemainingCapacity(queueName);
                
                stats.put("size", size);
                stats.put("capacity", capacity);
                stats.put("remainingCapacity", remaining);
                stats.put("utilizationPercent", (size * 100.0) / capacity);
                
                queueStats.put(queueName, stats);
            }
            details.put("queues", queueStats);
            
            // Add adaptive capacity information
            AdaptiveQueueCapacity adaptiveCapacity = queueService.getAdaptiveCapacity();
            Map<String, Object> capacityInfo = new HashMap<>();
            capacityInfo.put("currentCapacity", capacity);
            capacityInfo.put("minCapacity", adaptiveCapacity.getMinCapacity());
            capacityInfo.put("maxCapacity", adaptiveCapacity.getMaxCapacity());
            capacityInfo.put("heapUtilization", String.format("%.2f%%", adaptiveCapacity.getCurrentHeapUtilization() * 100));
            capacityInfo.put("memoryPressureHigh", adaptiveCapacity.isMemoryPressureHigh());
            details.put("adaptiveCapacity", capacityInfo);
            
            // Get active queues from consumer
            details.put("activeQueues", messageConsumer.getActiveQueues());
            
            // Determine overall health status
            if (shutdown) {
                return Health.down()
                        .withDetail("reason", "Service is shutting down")
                        .withDetails(details)
                        .build();
            }
            
            if (!consumerRunning) {
                return Health.down()
                        .withDetail("reason", "Consumer is not running")
                        .withDetails(details)
                        .build();
            }
            
            // Check if any queue is near capacity (>90%)
            for (Map<String, Object> stats : queueStats.values()) {
                double utilization = (double) stats.get("utilizationPercent");
                if (utilization > 90) {
                    return Health.down()
                            .withDetail("reason", "Queue utilization exceeds 90%")
                            .withDetails(details)
                            .build();
                }
            }
            
            return Health.up()
                    .withDetails(details)
                    .build();
                    
        } catch (Exception e) {
            return Health.down()
                    .withException(e)
                    .build();
        }
    }
}
