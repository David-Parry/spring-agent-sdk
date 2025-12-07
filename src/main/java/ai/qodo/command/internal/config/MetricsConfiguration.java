/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for bounded caches used in metrics collection.
 * Prevents unbounded memory growth by limiting the number of cached metric instances.
 */
@Configuration
public class MetricsConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MetricsConfiguration.class);

    @Value("${qodo.metrics.cache.max-size:1000}")
    private int maxCacheSize;

    @Value("${qodo.metrics.cache.expire-after-access-minutes:60}")
    private int expireAfterAccessMinutes;

    @Value("${qodo.metrics.cache.record-stats:true}")
    private boolean recordStats;

    /**
     * Creates a bounded cache for tool invocation Counter metrics.
     *
     * @return Cache instance for invocation Counter metrics
     */
    @Bean(name = "toolInvocationCounterCache")
    public Cache<String, Counter> toolInvocationCounterCache() {
        logger.info("Initializing tool invocation counter cache with max size: {}, expire after access: {} minutes",
                maxCacheSize, expireAfterAccessMinutes);
        return buildCache("tool_invocation_counter");
    }

    /**
     * Creates a bounded cache for tool success Counter metrics.
     *
     * @return Cache instance for success Counter metrics
     */
    @Bean(name = "toolSuccessCounterCache")
    public Cache<String, Counter> toolSuccessCounterCache() {
        logger.info("Initializing tool success counter cache with max size: {}, expire after access: {} minutes",
                maxCacheSize, expireAfterAccessMinutes);
        return buildCache("tool_success_counter");
    }

    /**
     * Creates a bounded cache for tool failure Counter metrics.
     *
     * @return Cache instance for failure Counter metrics
     */
    @Bean(name = "toolFailureCounterCache")
    public Cache<String, Counter> toolFailureCounterCache() {
        logger.info("Initializing tool failure counter cache with max size: {}, expire after access: {} minutes",
                maxCacheSize, expireAfterAccessMinutes);
        return buildCache("tool_failure_counter");
    }

    /**
     * Creates a bounded cache for Timer metrics.
     * Used to store tool execution time measurements.
     *
     * @return Cache instance for Timer metrics
     */
    @Bean(name = "toolExecutionTimerCache")
    public Cache<String, Timer> toolExecutionTimerCache() {
        logger.info("Initializing tool execution timer cache with max size: {}, expire after access: {} minutes",
                maxCacheSize, expireAfterAccessMinutes);
        return buildCache("tool_execution_timer");
    }

    /**
     * Builds a Caffeine cache with configured settings.
     *
     * @param metricType the type of metric (for logging purposes)
     * @param <V> the value type stored in the cache
     * @return configured Cache instance
     */
    private <V> Cache<String, V> buildCache(String metricType) {
        Caffeine<String, V> builder = Caffeine.newBuilder()
                .maximumSize(maxCacheSize)
                .expireAfterAccess(Duration.ofMinutes(expireAfterAccessMinutes))
                .removalListener((RemovalListener<String, V>) (key, value, cause) -> {
                    if (cause == RemovalCause.SIZE || cause == RemovalCause.EXPIRED) {
                        logger.warn("Evicting {} metric for key '{}' due to: {}. " +
                                        "Consider increasing cache size if this happens frequently.",
                                metricType, key, cause);
                        
                        // Log final value before eviction for counters
                        if (value instanceof Counter counter) {
                            logger.debug("Final count for evicted counter '{}': {}", key, counter.count());
                        } else if (value instanceof Timer timer) {
                            logger.debug("Final stats for evicted timer '{}': count={}, total={}",
                                    key, timer.count(), timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
                        }
                    }
                });

        if (recordStats) {
            builder.recordStats();
        }

        return builder.build();
    }
}
