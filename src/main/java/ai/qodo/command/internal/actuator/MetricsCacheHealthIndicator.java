/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.actuator;

import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for metrics cache utilization.
 * Reports unhealthy status when cache utilization exceeds threshold.
 */
@Component
public class MetricsCacheHealthIndicator implements HealthIndicator {

    private final Cache<String, Counter> invocationCounterCache;
    private final Cache<String, Counter> successCounterCache;
    private final Cache<String, Counter> failureCounterCache;
    private final Cache<String, Timer> timerCache;
    private final int maxCacheSize;
    private final double warningThreshold;
    private final double criticalThreshold;

    /**
     * Constructs the health indicator.
     *
     * @param invocationCounterCache cache for invocation counters
     * @param successCounterCache cache for success counters
     * @param failureCounterCache cache for failure counters
     * @param timerCache cache for timers
     * @param maxCacheSize maximum cache size from configuration
     */
    public MetricsCacheHealthIndicator(
            @Qualifier("toolInvocationCounterCache") Cache<String, Counter> invocationCounterCache,
            @Qualifier("toolSuccessCounterCache") Cache<String, Counter> successCounterCache,
            @Qualifier("toolFailureCounterCache") Cache<String, Counter> failureCounterCache,
            @Qualifier("toolExecutionTimerCache") Cache<String, Timer> timerCache,
            @Value("${qodo.metrics.cache.max-size:1000}") int maxCacheSize) {
        this.invocationCounterCache = invocationCounterCache;
        this.successCounterCache = successCounterCache;
        this.failureCounterCache = failureCounterCache;
        this.timerCache = timerCache;
        this.maxCacheSize = maxCacheSize;
        this.warningThreshold = 0.80; // 80%
        this.criticalThreshold = 0.95; // 95%
    }

    @Override
    public Health health() {
        long invocationSize = invocationCounterCache.estimatedSize();
        long successSize = successCounterCache.estimatedSize();
        long failureSize = failureCounterCache.estimatedSize();
        long timerSize = timerCache.estimatedSize();

        double invocationUtilization = (invocationSize * 100.0) / maxCacheSize;
        double successUtilization = (successSize * 100.0) / maxCacheSize;
        double failureUtilization = (failureSize * 100.0) / maxCacheSize;
        double timerUtilization = (timerSize * 100.0) / maxCacheSize;

        double maxUtilization = Math.max(
                Math.max(invocationUtilization, successUtilization),
                Math.max(failureUtilization, timerUtilization)
        );

        Health.Builder builder = Health.up();

        // Determine health status based on utilization
        if (maxUtilization >= criticalThreshold * 100) {
            builder = Health.down()
                    .withDetail("status", "CRITICAL")
                    .withDetail("message", "Cache utilization critical - evictions likely occurring");
        } else if (maxUtilization >= warningThreshold * 100) {
            builder = Health.up()
                    .withDetail("status", "WARNING")
                    .withDetail("message", "Cache utilization high - consider increasing cache size");
        } else {
            builder.withDetail("status", "HEALTHY");
        }

        // Add cache statistics
        builder.withDetail("max_cache_size", maxCacheSize)
                .withDetail("invocation_counter_cache", buildCacheDetails(invocationCounterCache, invocationUtilization))
                .withDetail("success_counter_cache", buildCacheDetails(successCounterCache, successUtilization))
                .withDetail("failure_counter_cache", buildCacheDetails(failureCounterCache, failureUtilization))
                .withDetail("execution_timer_cache", buildCacheDetails(timerCache, timerUtilization));

        return builder.build();
    }

    /**
     * Builds detailed cache statistics.
     *
     * @param cache the cache to inspect
     * @param utilization the utilization percentage
     * @return cache details map
     */
    private Object buildCacheDetails(Cache<?, ?> cache, double utilization) {
        var details = new java.util.HashMap<String, Object>();
        details.put("size", cache.estimatedSize());
        details.put("utilization_percent", String.format("%.2f%%", utilization));

        if (cache.stats() != null) {
            var stats = cache.stats();
            details.put("hit_rate", String.format("%.2f%%", stats.hitRate() * 100));
            details.put("eviction_count", stats.evictionCount());
            details.put("hit_count", stats.hitCount());
            details.put("miss_count", stats.missCount());
        }

        return details;
    }
}
