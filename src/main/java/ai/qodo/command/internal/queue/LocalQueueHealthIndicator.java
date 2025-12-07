/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.queue;

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
@ConditionalOnProperty(name = "messaging.provider", havingValue = "local")
public class LocalQueueHealthIndicator implements HealthIndicator {
    
    private final LocalQueueService queueService;
    private final LocalMessageConsumer messageConsumer;
    private final LocalQueueProperties properties;
    
    public LocalQueueHealthIndicator(
            LocalQueueService queueService,
            LocalMessageConsumer messageConsumer,
            LocalQueueProperties properties) {
        this.queueService = queueService;
        this.messageConsumer = messageConsumer;
        this.properties = properties;
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
            for (String queueName : queueService.getQueueNames()) {
                Map<String, Object> stats = new HashMap<>();
                int size = queueService.getQueueSize(queueName);
                int remaining = queueService.getRemainingCapacity(queueName);
                int capacity = properties.getQueueCapacity();
                
                stats.put("size", size);
                stats.put("capacity", capacity);
                stats.put("remainingCapacity", remaining);
                stats.put("utilizationPercent", (size * 100.0) / capacity);
                
                queueStats.put(queueName, stats);
            }
            details.put("queues", queueStats);
            
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
