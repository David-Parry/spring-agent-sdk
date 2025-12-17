/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.service;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Health indicator for Kafka messaging system.
 * Reports the health status of the Kafka cluster connection.
 * Activated when messaging.provider is set to "kafka".
 */
@Component
@ConditionalOnProperty(name = "messaging.provider", havingValue = "kafka")
public class KafkaHealthIndicator implements HealthIndicator {
    
    private final KafkaAdmin kafkaAdmin;
    
    /**
     * Constructs a KafkaHealthIndicator with the given KafkaAdmin.
     * 
     * @param kafkaAdmin The Kafka admin client for cluster operations
     */
    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }
    
    @Override
    public Health health() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DescribeClusterResult clusterResult = adminClient.describeCluster();
            
            Map<String, Object> details = new HashMap<>();
            details.put("clusterId", clusterResult.clusterId().get(5, TimeUnit.SECONDS));
            details.put("nodeCount", clusterResult.nodes().get(5, TimeUnit.SECONDS).size());
            details.put("controller", clusterResult.controller().get(5, TimeUnit.SECONDS).id());
            
            return Health.up()
                    .withDetails(details)
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withException(e)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
