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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global MCP metrics component that tracks MCP server and tool execution statistics.
 * This singleton provides a centralized view of all MCP operations including
 * server connections, tool invocations, and execution performance.
 *
 * <p>Exposes the following metrics:
 * <ul>
 *   <li>{@code qodo_mcp_active_servers} - Number of active MCP server connections</li>
 *   <li>{@code qodo_mcp_initialized_servers} - Number of successfully initialized MCP servers</li>
 *   <li>{@code qodo_mcp_failed_servers} - Number of MCP servers that failed to initialize</li>
 *   <li>{@code qodo_mcp_servers_no_tools} - Number of MCP servers with no tools registered</li>
 *   <li>{@code qodo_mcp_tool_invocations_total} - Total number of tool invocations (tagged by server and tool)</li>
 *   <li>{@code qodo_mcp_tool_invocations_success} - Number of successful tool invocations (tagged by server and tool)</li>
 *   <li>{@code qodo_mcp_tool_invocations_failure} - Number of failed tool invocations (tagged by server and tool)</li>
 *   <li>{@code qodo_mcp_tool_execution_time} - Timer for tool execution duration (tagged by server and tool)</li>
 *   <li>{@code qodo_mcp_registered_tools} - Total number of registered tools across all servers</li>
 * </ul>
 *
 * <p>Thread-safe: Uses AtomicInteger and Caffeine Cache for concurrent metric tracking.
 * The cache is bounded to prevent memory leaks from unlimited metric growth.
 */
@Component
public class McpMetrics {

    private static final Logger logger = LoggerFactory.getLogger(McpMetrics.class);

    private final MeterRegistry registry;
    private final AtomicInteger activeServers = new AtomicInteger(0);
    private final AtomicInteger initializedServers = new AtomicInteger(0);
    private final AtomicInteger failedServers = new AtomicInteger(0);
    private final AtomicInteger serversNoTools = new AtomicInteger(0);
    private final AtomicInteger registeredTools = new AtomicInteger(0);
    
    // Bounded caches for counters and timers to prevent memory leaks
    private final Cache<String, Counter> toolInvocationCounters;
    private final Cache<String, Counter> toolSuccessCounters;
    private final Cache<String, Counter> toolFailureCounters;
    private final Cache<String, Timer> toolExecutionTimers;

    /**
     * Constructs the global MCP metrics component and registers the gauges.
     *
     * @param registry the Micrometer MeterRegistry to register metrics with
     * @param invocationCounterCache bounded cache for invocation Counter metrics
     * @param successCounterCache bounded cache for success Counter metrics
     * @param failureCounterCache bounded cache for failure Counter metrics
     * @param timerCache bounded cache for Timer metrics
     */
    public McpMetrics(MeterRegistry registry,
                      @Qualifier("toolInvocationCounterCache") Cache<String, Counter> invocationCounterCache,
                      @Qualifier("toolSuccessCounterCache") Cache<String, Counter> successCounterCache,
                      @Qualifier("toolFailureCounterCache") Cache<String, Counter> failureCounterCache,
                      @Qualifier("toolExecutionTimerCache") Cache<String, Timer> timerCache) {
        this.registry = registry;
        this.toolInvocationCounters = invocationCounterCache;
        this.toolSuccessCounters = successCounterCache;
        this.toolFailureCounters = failureCounterCache;
        this.toolExecutionTimers = timerCache;
        
        // Register gauges for server metrics
        Gauge.builder("qodo_mcp_active_servers", activeServers, AtomicInteger::get)
                .description("Number of active MCP server connections in this JVM")
                .register(registry);

        Gauge.builder("qodo_mcp_initialized_servers", initializedServers, AtomicInteger::get)
                .description("Number of successfully initialized MCP servers")
                .register(registry);

        Gauge.builder("qodo_mcp_failed_servers", failedServers, AtomicInteger::get)
                .description("Number of MCP servers that failed to initialize")
                .register(registry);

        Gauge.builder("qodo_mcp_servers_no_tools", serversNoTools, AtomicInteger::get)
                .description("Number of MCP servers with no tools registered")
                .register(registry);

        Gauge.builder("qodo_mcp_registered_tools", registeredTools, AtomicInteger::get)
                .description("Total number of registered tools across all MCP servers")
                .register(registry);
    }

    /**
     * Increment the active servers counter.
     * Called when a new MCP server connection is successfully established.
     *
     * <p>Thread-safe: Can be called concurrently from multiple threads.
     */
    public void incrementActiveServers() {
        activeServers.incrementAndGet();
    }

    /**
     * Decrement the active servers counter.
     * Called when an MCP server connection is closed.
     *
     * <p>Thread-safe: Can be called concurrently from multiple threads.
     */
    public void decrementActiveServers() {
        activeServers.decrementAndGet();
    }

    /**
     * Increment the initialized servers counter.
     * Called when an MCP server is successfully initialized with tools.
     *
     * <p>Thread-safe: Can be called concurrently from multiple threads.
     */
    public void incrementInitializedServers() {
        initializedServers.incrementAndGet();
    }

    /**
     * Increment the failed servers counter.
     * Called when an MCP server fails to initialize.
     *
     * <p>Thread-safe: Can be called concurrently from multiple threads.
     */
    public void incrementFailedServers() {
        failedServers.incrementAndGet();
    }

    /**
     * Increment the servers with no tools counter.
     * Called when an MCP server initializes but has no tools registered.
     *
     * <p>Thread-safe: Can be called concurrently from multiple threads.
     */
    public void incrementServersNoTools() {
        serversNoTools.incrementAndGet();
    }

    /**
     * Set the total number of registered tools.
     * Called when the tool registry is refreshed.
     *
     * @param count the total number of registered tools
     */
    public void setRegisteredTools(int count) {
        registeredTools.set(count);
    }

    /**
     * Record a tool invocation.
     * Increments the total invocation counter for the specific server and tool.
     *
     * @param serverName the name of the MCP server (must not be null)
     * @param toolName the name of the tool being invoked (must not be null)
     */
    public void recordToolInvocation(String serverName, String toolName) {
        if (serverName == null || toolName == null) {
            // Log warning but don't throw exception to avoid disrupting the flow
            return;
        }
        String key = serverName + ":" + toolName;
        Counter counter = toolInvocationCounters.get(key, k -> 
            Counter.builder("qodo_mcp_tool_invocations_total")
                    .description("Total number of MCP tool invocations")
                    .tag("server", serverName)
                    .tag("tool", toolName)
                    .register(registry)
        );
        if (counter != null) {
            counter.increment();
        }
    }

    /**
     * Record a successful tool invocation.
     * Increments the success counter for the specific server and tool.
     *
     * @param serverName the name of the MCP server (must not be null)
     * @param toolName the name of the tool that succeeded (must not be null)
     */
    public void recordToolSuccess(String serverName, String toolName) {
        if (serverName == null || toolName == null) {
            // Log warning but don't throw exception to avoid disrupting the flow
            return;
        }
        String key = serverName + ":" + toolName;
        Counter counter = toolSuccessCounters.get(key, k -> 
            Counter.builder("qodo_mcp_tool_invocations_success")
                    .description("Number of successful MCP tool invocations")
                    .tag("server", serverName)
                    .tag("tool", toolName)
                    .register(registry)
        );
        if (counter != null) {
            counter.increment();
        }
    }

    /**
     * Record a failed tool invocation.
     * Increments the failure counter for the specific server and tool.
     *
     * @param serverName the name of the MCP server (must not be null)
     * @param toolName the name of the tool that failed (must not be null)
     */
    public void recordToolFailure(String serverName, String toolName) {
        if (serverName == null || toolName == null) {
            // Log warning but don't throw exception to avoid disrupting the flow
            return;
        }
        String key = serverName + ":" + toolName;
        Counter counter = toolFailureCounters.get(key, k -> 
            Counter.builder("qodo_mcp_tool_invocations_failure")
                    .description("Number of failed MCP tool invocations")
                    .tag("server", serverName)
                    .tag("tool", toolName)
                    .register(registry)
        );
        if (counter != null) {
            counter.increment();
        }
    }

    /**
     * Get a timer for recording tool execution time.
     * Creates and caches a timer for the specific server and tool if it doesn't exist.
     *
     * @param serverName the name of the MCP server (must not be null)
     * @param toolName the name of the tool (must not be null)
     * @return Timer instance for recording execution time, or null if parameters are invalid
     */
    public Timer getToolExecutionTimer(String serverName, String toolName) {
        if (serverName == null || toolName == null) {
            // Return a no-op timer or handle gracefully
            return Timer.builder("qodo_mcp_tool_execution_time_noop")
                    .description("No-op timer for invalid tool invocations")
                    .tag("server", "unknown")
                    .tag("tool", "unknown")
                    .register(registry);
        }
        String key = serverName + ":" + toolName;
        return toolExecutionTimers.get(key, k -> 
            Timer.builder("qodo_mcp_tool_execution_time")
                    .description("Time taken to execute MCP tool")
                    .tag("server", serverName)
                    .tag("tool", toolName)
                    .register(registry)
        );
    }

    /**
     * Get the current number of active servers.
     * Primarily for testing and debugging purposes.
     *
     * @return the current count of active MCP servers
     */
    public int getActiveServers() {
        return activeServers.get();
    }

    /**
     * Get the current number of initialized servers.
     * Primarily for testing and debugging purposes.
     *
     * @return the current count of initialized MCP servers
     */
    public int getInitializedServers() {
        return initializedServers.get();
    }

    /**
     * Get the current number of failed servers.
     * Primarily for testing and debugging purposes.
     *
     * @return the current count of failed MCP servers
     */
    public int getFailedServers() {
        return failedServers.get();
    }

    /**
     * Get the current number of servers with no tools.
     * Primarily for testing and debugging purposes.
     *
     * @return the current count of MCP servers with no tools
     */
    public int getServersNoTools() {
        return serversNoTools.get();
    }

    /**
     * Get the current number of registered tools.
     * Primarily for testing and debugging purposes.
     *
     * @return the current count of registered tools
     */
    public int getRegisteredTools() {
        return registeredTools.get();
    }
}
