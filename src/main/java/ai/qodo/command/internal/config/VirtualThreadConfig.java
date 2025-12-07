/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Configuration for virtual threads support
 * Provides optimized executors when virtual threads are enabled
 */
@Configuration
@EnableAsync
public class VirtualThreadConfig {

    /**
     * Creates a virtual thread executor when virtual threads are enabled
     * This executor is used for async processing in webhooks and other background tasks
     */
    @Bean("taskExecutor")
    @ConditionalOnProperty(name = "spring.threads.virtual.enabled", havingValue = "true")
    public Executor virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Fallback executor for when virtual threads are not enabled
     * Uses a cached thread pool with reasonable defaults
     */
    @Bean("taskExecutor")
    @ConditionalOnProperty(name = "spring.threads.virtual.enabled", havingValue = "false", matchIfMissing = true)
    public Executor defaultExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "async-task-");
            t.setDaemon(true);
            return t;
        });
    }
}