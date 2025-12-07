/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Global WebSocket metrics component that tracks JVM-wide connection statistics.
 * This singleton provides a centralized view of all WebSocket connections,
 * complementing the per-instance metrics in WebSocketService.
 *
 * <p>Exposes metrics:
 * <ul>
 *   <li>{@code qodo_ws_active_connections} - Active WebSocket connections</li>
 *   <li>{@code qodo_ws_ready_signal_wait_time} - Time waiting for READY signal</li>
 *   <li>{@code qodo_ws_ready_signal_timeouts_total} - Total READY signal timeouts</li>
 *   <li>{@code qodo_ws_ready_signal_received_total} - Total READY signals received</li>
 * </ul>
 *
 * <p>Thread-safe: Uses ConcurrentHashMap for tracking connections by session ID.
 * <p>Includes automatic cleanup of stale connections that may not have been properly removed.
 */
@Component
public class WebSocketMetrics {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketMetrics.class);
    
    // Track active connections by session ID with timestamp for cleanup
    private final ConcurrentHashMap<String, ConnectionInfo> activeConnections = new ConcurrentHashMap<>();
    
    // Scheduled executor for periodic cleanup
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-metrics-cleanup");
        t.setDaemon(true);
        return t;
    });
    
    // Maximum age for a connection before considering it stale (30 minutes)
    private static final Duration STALE_CONNECTION_THRESHOLD = Duration.ofMinutes(30);
    
    private final Counter readySignalTimeouts;
    private final Counter readySignalReceived;
    private final Timer readySignalWaitTime;
    private final Counter normalCloses;
    private final Counter abnormalCloses;

    /**
     * Constructs the global metrics component and registers the active connections gauge.
     *
     * @param registry the Micrometer MeterRegistry to register metrics with
     */
    public WebSocketMetrics(MeterRegistry registry) {
        Gauge.builder("qodo_ws_active_connections", activeConnections, ConcurrentHashMap::size)
                .description("Number of active WebSocket connections in this JVM")
                .register(registry);
        
        this.readySignalWaitTime = Timer.builder("qodo_ws_ready_signal_wait_time")
                .description("Time spent waiting for READY signal from server")
                .register(registry);
        
        this.readySignalTimeouts = Counter.builder("qodo_ws_ready_signal_timeouts_total")
                .description("Total number of READY signal timeouts")
                .register(registry);
        
        this.readySignalReceived = Counter.builder("qodo_ws_ready_signal_received_total")
                .description("Total number of READY signals received from server")
                .register(registry);
        
        this.normalCloses = Counter.builder("qodo_ws_normal_closes_total")
                .description("Total number of normal WebSocket closures (status 1000/1001)")
                .register(registry);
        
        this.abnormalCloses = Counter.builder("qodo_ws_abnormal_closes_total")
                .description("Total number of abnormal WebSocket closures (status != 1000/1001)")
                .register(registry);
    }
    
    /**
     * Starts the periodic cleanup task after bean construction.
     */
    @PostConstruct
    public void startCleanupTask() {
        // Schedule periodic cleanup every 5 minutes
        cleanupScheduler.scheduleWithFixedDelay(this::cleanupStaleConnections, 5, 5, TimeUnit.MINUTES);
        logger.info("Started WebSocket metrics cleanup task (runs every 5 minutes)");
    }
    
    /**
     * Stops the cleanup scheduler on shutdown.
     */
    @PreDestroy
    public void stopCleanupTask() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Stopped WebSocket metrics cleanup task");
    }
    
    /**
     * Removes connections that have been registered for longer than the stale threshold.
     * This is a safety mechanism to clean up any connections that weren't properly removed.
     */
    private void cleanupStaleConnections() {
        Instant now = Instant.now();
        int removedCount = 0;
        
        for (Map.Entry<String, ConnectionInfo> entry : activeConnections.entrySet()) {
            String sessionId = entry.getKey();
            ConnectionInfo info = entry.getValue();
            Duration age = Duration.between(info.connectedAt, now);
            
            if (age.compareTo(STALE_CONNECTION_THRESHOLD) > 0) {
                if (activeConnections.remove(sessionId, info)) {
                    removedCount++;
                    logger.warn("Removed stale WebSocket connection for session: {} (age: {} minutes)", 
                               sessionId, age.toMinutes());
                }
            }
        }
        
        if (removedCount > 0) {
            logger.info("Cleanup task removed {} stale WebSocket connections (remaining: {})", 
                       removedCount, activeConnections.size());
        } else {
            logger.debug("Cleanup task found no stale connections (active: {})", activeConnections.size());
        }
    }

    /**
     * Register a new WebSocket connection by session ID.
     * Called when a new WebSocket connection is successfully established.
     *
     * @param sessionId the unique session identifier
     * @return true if this is a new connection, false if session was already registered
     */
    public boolean addConnection(String sessionId) {
        if (sessionId == null) {
            logger.warn("Attempted to add connection with null session ID");
            return false;
        }
        
        ConnectionInfo newInfo = new ConnectionInfo(Instant.now());
        ConnectionInfo previous = activeConnections.put(sessionId, newInfo);
        boolean isNew = (previous == null);
        
        if (isNew) {
            logger.info("WebSocket connection added for session: {} (total: {})", 
                       sessionId, activeConnections.size());
        } else {
            Duration previousAge = Duration.between(previous.connectedAt, Instant.now());
            logger.warn("WebSocket connection already existed for session: {} (age: {} minutes, total: {})", 
                       sessionId, previousAge.toMinutes(), activeConnections.size());
        }
        
        return isNew;
    }

    /**
     * Remove a WebSocket connection by session ID.
     * Called when a WebSocket connection is closed or fails.
     *
     * @param sessionId the unique session identifier
     * @return true if connection was removed, false if it didn't exist
     */
    public boolean removeConnection(String sessionId) {
        if (sessionId == null) {
            logger.warn("Attempted to remove connection with null session ID");
            return false;
        }
        
        ConnectionInfo removed = activeConnections.remove(sessionId);
        boolean wasPresent = (removed != null);
        
        if (wasPresent) {
            Duration connectionAge = Duration.between(removed.connectedAt, Instant.now());
            logger.info("WebSocket connection removed for session: {} (age: {} seconds, total: {})", 
                       sessionId, connectionAge.toSeconds(), activeConnections.size());
        } else {
            logger.debug("WebSocket connection was not present for session: {} (total: {})", 
                        sessionId, activeConnections.size());
        }
        
        return wasPresent;
    }

    /**
     * Get the current number of active connections.
     * Primarily for testing and debugging purposes.
     *
     * @return the current count of active WebSocket connections
     */
    public int getActiveConnections() {
        return activeConnections.size();
    }
    
    /**
     * Check if a specific session is currently connected.
     *
     * @param sessionId the session ID to check
     * @return true if the session is connected, false otherwise
     */
    public boolean isConnected(String sessionId) {
        return sessionId != null && activeConnections.containsKey(sessionId);
    }

    /**
     * Record a READY signal timeout event.
     * Called when waiting for READY signal exceeds the configured timeout.
     *
     * <p>Thread-safe: Can be called concurrently from multiple sessions.
     */
    public void recordReadySignalTimeout() {
        readySignalTimeouts.increment();
    }

    /**
     * Record a READY signal received event.
     * Called when a READY signal is successfully received from the server.
     *
     * <p>Thread-safe: Can be called concurrently from multiple sessions.
     */
    public void recordReadySignalReceived() {
        readySignalReceived.increment();
    }

    /**
     * Get the timer for recording READY signal wait time.
     * Use this to measure how long it takes to receive a READY signal.
     *
     * @return the Timer instance for READY signal wait time
     */
    public Timer getReadySignalWaitTimer() {
        return readySignalWaitTime;
    }

    /**
     * Record a WebSocket close event with the given status code.
     * Categorizes the close as normal (1000, 1001) or abnormal (all others).
     *
     * @param statusCode the WebSocket close status code
     */
    public void recordCloseStatus(int statusCode) {
        if (statusCode == 1000 || statusCode == 1001) {
            normalCloses.increment();
        } else {
            abnormalCloses.increment();
        }
    }

    /**
     * Get the total count of normal closures.
     * Primarily for testing and debugging purposes.
     *
     * @return the count of normal closures (status 1000/1001)
     */
    public double getNormalCloses() {
        return normalCloses.count();
    }

    /**
     * Get the total count of abnormal closures.
     * Primarily for testing and debugging purposes.
     *
     * @return the count of abnormal closures (status != 1000/1001)
     */
    public double getAbnormalCloses() {
        return abnormalCloses.count();
    }
    
    /**
     * Internal class to track connection information including timestamp.
     */
    private static class ConnectionInfo {
        final Instant connectedAt;
        
        ConnectionInfo(Instant connectedAt) {
            this.connectedAt = connectedAt;
        }
    }
}