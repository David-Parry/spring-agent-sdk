/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.metrics;

import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Exports Caffeine cache statistics as Prometheus metrics.
 * Provides visibility into cache utilization, hit rates, and evictions.
 */
@Component
public class CacheMetricsExporter {

    /**
     * Registers cache metrics with the MeterRegistry.
     *
     * @param registry the Micrometer MeterRegistry
     * @param invocationCounterCache cache for invocation counters
     * @param successCounterCache cache for success counters
     * @param failureCounterCache cache for failure counters
     * @param timerCache cache for timers
     */
    public CacheMetricsExporter(MeterRegistry registry,
                                @Qualifier("toolInvocationCounterCache") Cache<String, Counter> invocationCounterCache,
                                @Qualifier("toolSuccessCounterCache") Cache<String, Counter> successCounterCache,
                                @Qualifier("toolFailureCounterCache") Cache<String, Counter> failureCounterCache,
                                @Qualifier("toolExecutionTimerCache") Cache<String, Timer> timerCache) {

        // Register cache size gauges
        registerCacheSizeGauge(registry, invocationCounterCache, "invocation_counter");
        registerCacheSizeGauge(registry, successCounterCache, "success_counter");
        registerCacheSizeGauge(registry, failureCounterCache, "failure_counter");
        registerCacheSizeGauge(registry, timerCache, "execution_timer");

        // Register cache statistics if available
        registerCacheStats(registry, invocationCounterCache, "invocation_counter");
        registerCacheStats(registry, successCounterCache, "success_counter");
        registerCacheStats(registry, failureCounterCache, "failure_counter");
        registerCacheStats(registry, timerCache, "execution_timer");
    }

    /**
     * Registers a gauge for cache size.
     *
     * @param registry the MeterRegistry
     * @param cache the cache to monitor
     * @param cacheType the type of cache (for tagging)
     */
    private void registerCacheSizeGauge(MeterRegistry registry, Cache<?, ?> cache, String cacheType) {
        Gauge.builder("qodo_metrics_cache_size", cache, Cache::estimatedSize)
                .description("Current size of metrics cache")
                .tag("cache_type", cacheType)
                .register(registry);
    }

    /**
     * Registers cache statistics gauges if stats recording is enabled.
     *
     * @param registry the MeterRegistry
     * @param cache the cache to monitor
     * @param cacheType the type of cache (for tagging)
     */
    private void registerCacheStats(MeterRegistry registry, Cache<?, ?> cache, String cacheType) {
        if (cache.stats() != null) {
            // Eviction count
            Gauge.builder("qodo_metrics_cache_evictions_total",
                            cache, c -> (double) c.stats().evictionCount())
                    .description("Total number of cache evictions")
                    .tag("cache_type", cacheType)
                    .register(registry);

            // Hit count
            Gauge.builder("qodo_metrics_cache_hits_total",
                            cache, c -> (double) c.stats().hitCount())
                    .description("Total number of cache hits")
                    .tag("cache_type", cacheType)
                    .register(registry);

            // Miss count
            Gauge.builder("qodo_metrics_cache_misses_total",
                            cache, c -> (double) c.stats().missCount())
                    .description("Total number of cache misses")
                    .tag("cache_type", cacheType)
                    .register(registry);

            // Hit rate
            Gauge.builder("qodo_metrics_cache_hit_rate",
                            cache, c -> c.stats().hitRate())
                    .description("Cache hit rate (0.0 to 1.0)")
                    .tag("cache_type", cacheType)
                    .register(registry);

            // Load success count
            Gauge.builder("qodo_metrics_cache_load_success_total",
                            cache, c -> (double) c.stats().loadSuccessCount())
                    .description("Total number of successful cache loads")
                    .tag("cache_type", cacheType)
                    .register(registry);

            // Load failure count
            Gauge.builder("qodo_metrics_cache_load_failure_total",
                            cache, c -> (double) c.stats().loadFailureCount())
                    .description("Total number of failed cache loads")
                    .tag("cache_type", cacheType)
                    .register(registry);
        }
    }
}
