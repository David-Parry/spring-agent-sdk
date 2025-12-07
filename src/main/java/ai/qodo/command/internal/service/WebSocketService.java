/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.service;

import ai.qodo.command.internal.LifecycleState;
import ai.qodo.command.internal.api.TaskResponse;
import ai.qodo.command.internal.api.WireMsgRouteKey;
import ai.qodo.command.internal.config.QodoProperties;
import ai.qodo.command.internal.metrics.WebSocketMetrics;
import ai.qodo.command.internal.pojo.CommandSession;
import ai.qodo.command.internal.pojo.CommandSessionBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.*;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Robust WebSocket service with automatic reconnection and JMS transaction integration.
 * Attempts reconnection up to 3 times before throwing exception for JMS rollback.
 * Each instance manages exactly one connection (prototype scope).
 *
 * <p>Metrics exposed per instance (tagged by instance ID):
 * <ul>
 *   <li>{@code qodo_ws_connection_status} - Connection state (0=disconnected, 1=connected)</li>
 *   <li>{@code qodo_ws_last_pong_age_seconds} - Time since last pong received</li>
 *   <li>{@code qodo_ws_messages_received_total} - Total messages received (counter)</li>
 *   <li>{@code qodo_ws_messages_sent_total} - Total messages sent (counter)</li>
 * </ul>
 *
 * <p>Also updates global metrics via {@link WebSocketMetrics} for JVM-wide connection count.
 */
@Service
@Scope("prototype")
public class WebSocketService {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketService.class);
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final QodoProperties qodoProperties;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService executor;
    private final String instanceId;
    private final WebSocketMetrics globalMetrics;
    private final MeterRegistry meterRegistry;

    // Configurable timeout durations
    private final Duration pingInterval;
    private final Duration pongTimeout;
    private final Duration connectionTimeout;
    // Recovery state management
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final AtomicBoolean intentionalClose = new AtomicBoolean(false);
    private final AtomicBoolean expectedClose = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final WebSocketCircuitBreaker circuitBreaker;
    // Metrics fields
    private final AtomicInteger connectionStatus = new AtomicInteger(0);
    private final AtomicLong lastPongTimestamp = new AtomicLong(System.currentTimeMillis());
    // Lifecycle state tracking
    private volatile LifecycleState lifecycleState = LifecycleState.CREATED;
    // Single connection state - one connection per service instance
    private volatile WebSocketConnection connection;
    private ScheduledFuture<?> pingTask;
    private ScheduledFuture<?> pongTimeoutTask;
    // Last-known connection context (for reconnects)
    private volatile CommandSession lastSession;
    private volatile String lastToken;
    private volatile Consumer<TaskResponse> lastMessageHandler;
    private volatile Consumer<String> lastErrorHandler;
    // Track the current connection future for exception propagation
    private volatile CompletableFuture<WebSocket> connectionFuture;
    // Meter references for cleanup
    private Meter.Id connectionStatusMeterId;
    private Meter.Id lastPongAgeMeterId;
    private Meter.Id msgReceivedTotalMeterId;
    private Meter.Id msgSentTotalMeterId;

    // Metric instances
    private Counter msgReceivedTotal;
    private Counter msgSentTotal;


    public WebSocketService(QodoProperties qodoProperties, ObjectProvider<MeterRegistry> meterRegistryProvider,
                            WebSocketMetrics globalMetrics) {
        this.qodoProperties = qodoProperties;
        this.globalMetrics = globalMetrics;
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
        this.objectMapper = new ObjectMapper();
        this.instanceId = generateRandomPrefix();

        // Initialize configurable durations from properties
        this.pingInterval = Duration.ofSeconds(qodoProperties.getWebsocket().getPingIntervalSeconds());
        this.pongTimeout = Duration.ofSeconds(qodoProperties.getWebsocket().getPongTimeoutSeconds());
        this.connectionTimeout = Duration.ofSeconds(qodoProperties.getWebsocket().getConnectionTimeoutSeconds());

        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, instanceId + "-ws-scheduler");
            t.setDaemon(true);
            return t;
        });

        // Bounded thread pool with backpressure
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
        this.executor = new ThreadPoolExecutor(Math.max(2, cores / 2),  // Core pool size
                                               Math.max(4, cores),      // Maximum pool size
                                               60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(2000), r -> {
            Thread t = new Thread(r, instanceId + "-ws-exec");
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.CallerRunsPolicy());

        // Initialize circuit breaker with default settings
        this.circuitBreaker = new WebSocketCircuitBreaker();

        // Bind metrics if available
        bindMetricsIfEnabled();

        logger.info("[{}] WebSocketService initialized (ping={}s, pongTimeout={}s, connectTimeout={}s, " +
                            "maxReconnects={}, metrics={}, circuitBreaker=enabled)", instanceId,
                    pingInterval.toSeconds(), pongTimeout.toSeconds(), connectionTimeout.toSeconds(), qodoProperties
                .getWebsocket()
                .getMaxReconnectAttempts(), meterRegistry != null ? "enabled" : "disabled");
    }

    /**
     * Properly shuts down an executor service.
     */
    private static void shutdownExecutor(ExecutorService executor, String name) {
        if (executor == null || executor.isShutdown()) {
            return;
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("Executor {} did not terminate", name);
                }
            }
        } catch (InterruptedException ie) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * URL encodes a string for safe use in URLs.
     */
    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Registers all per-instance metrics with the MeterRegistry if available.
     * Each metric is tagged with this instance's unique ID for identification.
     */
    private void bindMetricsIfEnabled() {
        if (meterRegistry == null) {
            logger.debug("[{}] MeterRegistry not available, metrics disabled", instanceId);
            return;
        }

        Tags tags = Tags.of("instance", instanceId);

        // Connection status gauge (0=disconnected, 1=connected)
        Gauge connectionStatusGauge = Gauge
                .builder("qodo_ws_connection_status", connectionStatus, AtomicInteger::get)
                .description("WebSocket connection status (0=disconnected, 1=connected)")
                .tags(tags)
                .register(meterRegistry);
        connectionStatusMeterId = connectionStatusGauge.getId();

        // Last pong age gauge (seconds since last pong)
        Gauge lastPongAgeGauge = Gauge
                .builder("qodo_ws_last_pong_age_seconds", this,
                         service -> (System.currentTimeMillis() - service.lastPongTimestamp.get()) / 1000.0)
                .description("Seconds since last pong received")
                .tags(tags)
                .register(meterRegistry);
        lastPongAgeMeterId = lastPongAgeGauge.getId();

        // Message received counter
        msgReceivedTotal = Counter
                .builder("qodo_ws_messages_received_total")
                .description("Total TaskResponse messages received")
                .tags(tags)
                .register(meterRegistry);
        msgReceivedTotalMeterId = msgReceivedTotal.getId();

        // Message sent counter
        msgSentTotal = Counter
                .builder("qodo_ws_messages_sent_total")
                .description("Total messages sent")
                .tags(tags)
                .register(meterRegistry);
        msgSentTotalMeterId = msgSentTotal.getId();

        logger.info("[{}] Metrics bound for WebSocket instance", instanceId);
    }

    /**
     * Generates a random 5-character prefix for unique instance identification.
     */
    private String generateRandomPrefix() {
        Random random = new Random();
        StringBuilder prefix = new StringBuilder(5);
        for (int i = 0; i < 5; i++) {
            prefix.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return prefix.toString();
    }

    /**
     * Establishes a WebSocket connection with the specified parameters.
     * This instance manages a single connection.
     */
    public CompletableFuture<WebSocket> connect(CommandSession session, String token,
                                                Consumer<TaskResponse> messageHandler, Consumer<String> errorHandler) {
        return connect(session, token, messageHandler, errorHandler, null, false);
    }

    /**
     * Establishes a WebSocket connection with the specified parameters and reconnection callback.
     * This instance manages a single connection.
     *
     * @param reconnectionCallback Optional callback invoked when reconnection starts (can be null)
     */
    public CompletableFuture<WebSocket> connect(CommandSession session, String token,
                                                Consumer<TaskResponse> messageHandler, Consumer<String> errorHandler,
                                                Runnable reconnectionCallback) {
        return connect(session, token, messageHandler, errorHandler, reconnectionCallback, false);
    }

    /**
     * Internal connect method with explicit reconnection flag.
     *
     * @param isReconnect true if this is a reconnection attempt, false for initial connection
     */
    private CompletableFuture<WebSocket> connect(CommandSession session, String token,
                                                 Consumer<TaskResponse> messageHandler, Consumer<String> errorHandler,
                                                 Runnable reconnectionCallback, boolean isReconnect) {

        // Check circuit breaker before attempting connection
        if (!circuitBreaker.shouldAttemptConnection()) {
            String message = String.format("Circuit breaker is OPEN - blocking connection attempt. %s",
                                           circuitBreaker.getStatusMessage());
            logger.error("[{}] {}", instanceId, message);

            CompletableFuture<WebSocket> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new CommandException(message));
            return failedFuture;
        }

        // Create and store the connection future for exception propagation
        this.connectionFuture = new CompletableFuture<>();

        // Update lifecycle state
        lifecycleState = LifecycleState.CONNECTING;
        logger.info("[{}] Lifecycle state: {} -> CONNECTING (circuit breaker: {})", instanceId, lifecycleState,
                    circuitBreaker.getState());

        // Check if this instance already has a connection
        if (connection != null && connection.isConnected().get()) {
            logger.warn("[{}] Instance already has an active connection for session {}, closing old connection",
                        instanceId, connection.sessionId());
            disconnectSession(connection.sessionId());
        }

        String effectiveToken = token != null ? token : qodoProperties.getWebsocket().getToken();
        if (effectiveToken == null || effectiveToken.isEmpty()) {
            throw new IllegalArgumentException("WebSocket token is required");
        }

        // Persist context for potential reconnects
        this.lastSession = session;
        this.lastToken = effectiveToken;
        this.lastMessageHandler = messageHandler;
        this.lastErrorHandler = errorHandler;

        // Only reset reconnect attempts if this is NOT a reconnection
        if (!isReconnect) {
            this.reconnectAttempts.set(0);
        }
        this.intentionalClose.set(false);

        // Generate WebSocket URL using the session (includes checkpoint_id if this is a reconnect)
        String wsBase = qodoProperties.getBaseUrl().replaceFirst("^http", "ws").replaceAll("/+$", "");
        String wsUrl = session.generateWebSocketUrl(wsBase, isReconnect);

        logger.info("[{}] Connecting to WebSocket for session {} (reconnect={}, checkpoint_id={}): {}", instanceId,
                    session.sessionId(), isReconnect, isReconnect ? session.checkPointId() : "N/A", wsUrl);

        HttpClient client = HttpClient.newBuilder().connectTimeout(connectionTimeout).build();
        WebSocketListener listener = new WebSocketListener(session.sessionId(), messageHandler, errorHandler);

        // Build the WebSocket connection asynchronously
        client
                .newWebSocketBuilder()
                .header("Authorization", "Bearer " + effectiveToken)
                .buildAsync(URI.create(wsUrl), listener)
                .thenApply(webSocket -> {
                    // Create single connection for this instance
                    connection = new WebSocketConnection(session.sessionId(), session.requestId(), webSocket);
                    connection.isConnected().set(true);
                    connection.lastActivity().set(Instant.now());

                    // Update lifecycle state
                    lifecycleState = LifecycleState.CONNECTED;
                    logger.info("[{}] Lifecycle state: CONNECTING -> CONNECTED for session: {}", instanceId,
                                session.sessionId());

                    // Update metrics - Note: increment happens in onOpen callback
                    connectionStatus.set(1);

                    // Record successful connection in circuit breaker
                    circuitBreaker.recordSuccess();
                    logger.debug("[{}] Circuit breaker recorded success: {}", instanceId,
                                 circuitBreaker.getStatusMessage());

                    // Start ping/pong mechanism
                    startPingPong();

                    logger.info("[{}] WebSocket connection established successfully for session: {}", instanceId,
                                session.sessionId());

                    return webSocket;
                })
                .exceptionally(throwable -> {
                    logger.error("[{}] Failed to establish WebSocket connection for session: {}", instanceId,
                                 session.sessionId(), throwable);

                    // Reset lifecycle state on failure
                    lifecycleState = LifecycleState.CREATED;

                    // Record failure in circuit breaker
                    circuitBreaker.recordFailure();
                    logger.warn("[{}] Circuit breaker recorded failure: {}", instanceId,
                                circuitBreaker.getStatusMessage());

                    if (errorHandler != null) {
                        errorHandler.accept("Connection failed: " + throwable.getMessage());
                    }

                    // Schedule reconnect or complete future exceptionally
                    scheduleReconnect("initial connection failure");
                    return null;
                })
                .whenComplete((ws, error) -> {
                    // Complete the stored connection future with the result
                    if (error != null) {
                        if (!connectionFuture.isDone()) {
                            connectionFuture.completeExceptionally(error);
                        }
                    } else {
                        if (!connectionFuture.isDone()) {
                            connectionFuture.complete(ws);
                        }
                    }
                });

        // Return the stored connection future for exception propagation
        return connectionFuture;
    }

    /**
     * Sends a message through the WebSocket connection.
     */
    public CompletableFuture<Void> sendMessage(String sessionId, String message) {
        if (!validateSession(sessionId)) {
            return CompletableFuture.failedFuture(new IllegalStateException("WebSocket not connected for session: " + sessionId));
        }

        WebSocket webSocket = connection.webSocket();
        connection.lastActivity().set(Instant.now());

        // Only add newline if the message doesn't already end with one
        String msg = message;
        if (!message.endsWith("\n")) {
            msg = message + "\n";
        }

        logger.trace("[{}] Sending WebSocket session {} message {}", instanceId, sessionId, message);
        return webSocket.sendText(msg, true).thenRun(() -> {
            // Increment sent message counter
            if (msgSentTotal != null) {
                msgSentTotal.increment();
            }
            logger.debug("[{}] Message sent successfully for session: {} ({} bytes)", instanceId, sessionId,
                         message.length());
        }).exceptionally(throwable -> {
            logger.error("[{}] Failed to send WebSocket message for session: {}", instanceId, sessionId, throwable);
            scheduleReconnect("send failure");
            return null;
        });
    }

    /**
     * Sends a structured object as JSON through the WebSocket.
     */
    public CompletableFuture<Void> sendObject(WireMsgRouteKey route, String sessionId, Object object) {
        try {
            String json = objectMapper.writeValueAsString(object);
            String msg = route.name() + " " + json;
            if (logger.isDebugEnabled()) {
                logger.debug("Sending message on the wire length is {}", msg.length());
            } else if (logger.isTraceEnabled()) {
                logger.trace("Sending message on the wire raw {}", msg);
            }
            return sendMessage(sessionId, msg);
        } catch (JsonProcessingException e) {
            logger.error("[{}] Failed to serialize object to JSON for session: {}", instanceId, sessionId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Marks that the connection is expected to close (e.g., after receiving ENDNODE).
     * This prevents treating server-initiated closes as errors and blocks reconnection attempts.
     * Also initiates a graceful disconnect to ensure proper cleanup.
     */
    public void markExpectedClose() {
        expectedClose.set(true);
        intentionalClose.set(true); // Prevent reconnection attempts
        logger.info("[{}] Marked connection as expected to close (intentional=true, expected=true)", instanceId);

        // Schedule a graceful disconnect after a short delay to allow server to close first
        // This ensures cleanup happens even if server doesn't send proper close frame
        // Reduced delay from 2s to 500ms to minimize race condition window
        if (connection != null) {
            String sessionId = connection.sessionId();
            scheduler.schedule(() -> {
                if (connection != null && connection.sessionId().equals(sessionId)) {
                    logger.debug("[{}] Initiating graceful disconnect after ENDNODE for session: {}", instanceId,
                                 sessionId);
                    disconnectSession(sessionId, 1000, "Expected close after ENDNODE");
                }
            }, 500, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Closes the WebSocket connection.
     */
    public void disconnectSession(String sessionId) {
        disconnectSession(sessionId, 1000, "Normal closure");
    }

    /**
     * Closes the WebSocket connection with status code and reason.
     * Waits for the close handshake to complete to ensure proper closure.
     */
    public void disconnectSession(String sessionId, int statusCode, String reason) {
        logger.info("[{}] Disconnecting WebSocket for session {}: {} - {}", instanceId, sessionId, statusCode, reason);

        intentionalClose.set(true);
        stopPingPong();

        if (connection == null) {
            logger.debug("[{}] No connection found for session: {}", instanceId, sessionId);
            // Ensure metrics are cleaned up even if connection is null
            if (globalMetrics != null && sessionId != null) {
                boolean removed = globalMetrics.removeConnection(sessionId);
                if (removed) {
                    logger.info("[{}] Removed orphaned connection from global metrics for session: {}", instanceId,
                                sessionId);
                }
            }
            return;
        }

        if (!connection.sessionId().equals(sessionId)) {
            logger.warn("[{}] Session ID mismatch during disconnect. Expected: {}, Got: {}", instanceId,
                        connection.sessionId(), sessionId);
        }

        connection.isConnected().set(false);

        // Update local status
        connectionStatus.set(0);

        WebSocket webSocket = connection.webSocket();
        boolean closeHandshakeCompleted = false;

        if (webSocket != null) {
            try {
                // Wait for close handshake to complete with timeout
                webSocket.sendClose(statusCode, reason).get(5, TimeUnit.SECONDS);
                closeHandshakeCompleted = true;
                logger.info("[{}] WebSocket disconnected successfully for session: {} with status: {}", instanceId,
                            sessionId, statusCode);
            } catch (TimeoutException e) {
                logger.warn("[{}] Timeout waiting for WebSocket close handshake for session: {} - " + "connection " +
                                    "may" + " close with status 1006", instanceId, sessionId);
            } catch (InterruptedException e) {
                logger.warn("[{}] Interrupted while waiting for WebSocket close for session: {}", instanceId,
                            sessionId);
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                logger.warn("[{}] Error during WebSocket close handshake for session: {}", instanceId, sessionId,
                            e.getCause());
            }
        }

        // If close handshake didn't complete normally, ensure metrics are cleaned up
        // This handles cases where onClose callback might not be triggered
        if (!closeHandshakeCompleted && globalMetrics != null) {
            boolean removed = globalMetrics.removeConnection(sessionId);
            if (removed) {
                logger.info("[{}] Proactively removed connection from global metrics after failed close for session: "
                                    + "{}", instanceId, sessionId);
            }
        }

        // Clear connection reference AFTER close completes
        connection = null;
    }

    /**
     * Checks if the connection is active.
     */
    public boolean isConnected(String sessionId) {
        return connection != null && connection.sessionId().equals(sessionId) && connection.isConnected().get();
    }

    /**
     * Gets the current connection status.
     */
    public ConnectionStatus getConnectionStatus(String sessionId) {
        if (reconnecting.get()) {
            return ConnectionStatus.RECONNECTING;
        }
        if (connection == null || !connection.sessionId().equals(sessionId)) {
            return ConnectionStatus.DISCONNECTED;
        }
        return connection.isConnected().get() ? ConnectionStatus.CONNECTED : ConnectionStatus.DISCONNECTED;
    }

    /**
     * Cleanup resources when service is destroyed.
     * Protected against premature destruction during active connection lifecycle.
     */
    @PreDestroy
    public void destroy() {
        logger.info("[{}] Destroy called - current lifecycle state: {}", instanceId, lifecycleState);

        // Prevent premature destruction during critical lifecycle states
        if (lifecycleState == LifecycleState.CONNECTING || lifecycleState == LifecycleState.CONNECTED || lifecycleState == LifecycleState.ACTIVE) {

            Duration timeSinceCreation = connection != null ?
                    Duration.between(connection.createdAt(), Instant.now()) : Duration.ZERO;

            logger.error("[{}] PREVENTING PREMATURE DESTROY - lifecycle state is {} " + "(connection age: {}ms). " +
                                 "This" + " indicates a bean lifecycle issue.", instanceId, lifecycleState,
                         timeSinceCreation.toMillis());

            // Don't disconnect - let the connection continue
            // Only clean up thread pools to prevent resource leaks
            logger.warn("[{}] Skipping disconnect but cleaning up executors", instanceId);
            stopPingPong();
            shutdownExecutor(scheduler, "scheduler");
            shutdownExecutor(executor, "executor");
            return;
        }

        // Normal shutdown path
        lifecycleState = LifecycleState.CLOSING;
        logger.info("[{}] Proceeding with normal shutdown", instanceId);

        // Store session ID before disconnecting for cleanup
        String sessionIdToCleanup = connection != null ? connection.sessionId() : null;

        // Disconnect the connection if present
        if (connection != null) {
            try {
                disconnectSession(connection.sessionId());
            } catch (Exception e) {
                logger.warn("[{}] Error during disconnect on destroy", instanceId, e);
            }
        }

        // Ensure global metrics are cleaned up even if disconnect failed
        if (sessionIdToCleanup != null && globalMetrics != null) {
            boolean removed = globalMetrics.removeConnection(sessionIdToCleanup);
            if (removed) {
                logger.info("[{}] Cleaned up global metrics for session {} during destroy", instanceId,
                            sessionIdToCleanup);
            }
        }

        stopPingPong();
        shutdownExecutor(scheduler, "scheduler");
        shutdownExecutor(executor, "executor");

        // Remove instance-specific meters to avoid time-series accumulation
        if (meterRegistry != null) {
            if (connectionStatusMeterId != null) {
                meterRegistry.remove(connectionStatusMeterId);
            }
            if (lastPongAgeMeterId != null) {
                meterRegistry.remove(lastPongAgeMeterId);
            }
            if (msgReceivedTotalMeterId != null) {
                meterRegistry.remove(msgReceivedTotalMeterId);
            }
            if (msgSentTotalMeterId != null) {
                meterRegistry.remove(msgSentTotalMeterId);
            }
            logger.debug("[{}] Metrics removed for instance", instanceId);
        }

        lifecycleState = LifecycleState.CLOSED;
        logger.info("[{}] WebSocket service shutdown complete", instanceId);
    }

    // ==================== Reconnection Logic ====================

    /**
     * Validates that the session is connected and matches.
     */
    private boolean validateSession(String sessionId) {
        if (connection == null || !connection.isConnected().get()) {
            logger.error("[{}] WebSocket not connected for session: {}", instanceId, sessionId);
            return false;
        }

        if (!connection.sessionId().equals(sessionId)) {
            logger.error("[{}] Session ID mismatch. Expected: {}, Got: {}", instanceId, connection.sessionId(),
                         sessionId);
            return false;
        }

        return true;
    }

    /**
     * Schedules a reconnection attempt or throws exception if max attempts reached.
     * This method will throw CommandException after MAX_RECONNECT_ATTEMPTS to trigger JMS rollback.
     */
    private void scheduleReconnect(String cause) {
        // Don't reconnect if intentionally closed
        if (intentionalClose.get()) {
            logger.debug("[{}] Intentional close, not reconnecting", instanceId);
            return;
        }

        // Prevent multiple concurrent reconnection attempts
        if (!reconnecting.compareAndSet(false, true)) {
            logger.debug("[{}] Reconnection already in progress", instanceId);
            return;
        }

        int attempts = reconnectAttempts.incrementAndGet();
        int maxAttempts = qodoProperties.getWebsocket().getMaxReconnectAttempts();

        // If max attempts reached, complete the connection future exceptionally to propagate to JMS thread
        if (attempts > maxAttempts) {
            logger.error("[{}] Max reconnect attempts ({}) exhausted for cause: {}", instanceId, maxAttempts, cause);
            reconnecting.set(false);

            // Complete the connection future exceptionally to propagate exception to JMS thread
            if (connectionFuture != null && !connectionFuture.isDone()) {
                CommandException exception = new CommandException(String.format("WebSocket connection failed after " +
                                                                                        "%d" + " reconnect attempts -" +
                                                                                        " %s", maxAttempts, cause));
                connectionFuture.completeExceptionally(exception);
                logger.info("[{}] Completed connection future exceptionally to trigger JMS rollback", instanceId);
            } else {
                logger.warn("[{}] Connection future is null or already done, cannot propagate exception", instanceId);
            }
            return;
        }

        Duration delay = calculateBackoffDelay(attempts);

        logger.warn("[{}] Scheduling reconnect attempt {}/{} in {}ms (cause: {})", instanceId, attempts, maxAttempts,
                    delay.toMillis(), cause);

        int maxAttemptsInner = qodoProperties.getWebsocket().getMaxReconnectAttempts();
        scheduler.schedule(() -> {
            try {
                if (lastSession == null || lastToken == null) {
                    logger.error("[{}] No prior context to reconnect", instanceId);
                    reconnecting.set(false);
                    // Throw exception as we can't reconnect without context
                    throw new CommandException("Cannot reconnect - no session context available");
                }

                // Create new session with fresh request ID for this reconnection attempt
                CommandSession reconnectSession = CommandSessionBuilder
                        .fromSessionWithNewRequestId(lastSession)
                        .build();

                logger.info("[{}] Attempting reconnection {}/{} for session {} with new request_id: {} (old: {})",
                            instanceId, attempts, maxAttemptsInner, reconnectSession.sessionId(),
                            reconnectSession.requestId(), lastSession.requestId());

                // Update lastSession with the new one for future reconnections
                lastSession = reconnectSession;

                // Pass true for isReconnect to include checkpoint_id in URL
                connect(reconnectSession, lastToken, lastMessageHandler, lastErrorHandler, null, true)
                        .thenAccept(ws -> {
                            if (ws != null && !ws.isInputClosed() && !ws.isOutputClosed()) {
                                logger.info("[{}] Successfully reconnected on attempt {}/{}", instanceId, attempts,
                                            maxAttemptsInner);
                                reconnectAttempts.set(0);
                                reconnecting.set(false);
                            } else {
                                // Connection failed, will retry or throw
                                reconnecting.set(false);
                                scheduleReconnect("reconnection failed");
                            }
                        })
                        .exceptionally(throwable -> {
                            logger.error("[{}] Reconnect attempt {}/{} failed", instanceId, attempts,
                                         maxAttemptsInner, throwable);
                            reconnecting.set(false);
                            // Schedule next attempt or throw
                            scheduleReconnect("reconnection exception");
                            return null;
                        });
            } catch (Exception e) {
                logger.error("[{}] Unexpected error during reconnect attempt {}/{}", instanceId, attempts,
                             maxAttemptsInner, e);
                reconnecting.set(false);
                // Schedule next attempt or throw
                scheduleReconnect("unexpected reconnection error");
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Calculates exponential backoff delay with jitter.
     */
    private Duration calculateBackoffDelay(int attempt) {
        long baseMillis = qodoProperties.getWebsocket().getInitialReconnectDelay().toMillis();
        long maxMillis = qodoProperties.getWebsocket().getMaxReconnectDelay().toMillis();

        // Exponential backoff: base * 2^(attempt-1)
        long exponentialDelay = Math.min(maxMillis, baseMillis * (1L << Math.min(10, attempt - 1)));

        // Add jitter (±20%)
        double jitterFactor = 0.8 + (ThreadLocalRandom.current().nextDouble() * 0.4);
        long delayWithJitter = (long) (exponentialDelay * jitterFactor);

        return Duration.ofMillis(Math.min(delayWithJitter, maxMillis));
    }

    // ==================== Ping/Pong Management ====================

    /**
     * Starts the ping/pong mechanism for connection health monitoring.
     */
    private void startPingPong() {
        stopPingPong(); // Stop any existing ping/pong tasks

        if (connection == null) {
            return;
        }

        connection.lastPongReceived().set(Instant.now());
        lastPongTimestamp.set(System.currentTimeMillis());

        // Schedule periodic ping messages
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (connection != null && connection.isConnected().get()) {
                    logger.trace("[{}] Sending ping for session: {}", instanceId, connection.sessionId());

                    connection
                            .webSocket()
                            .sendPing(ByteBuffer.wrap("ping".getBytes(StandardCharsets.UTF_8)))
                            .exceptionally(ex -> {
                                logger.warn("[{}] Ping send failed for session: {}", instanceId,
                                            connection.sessionId(), ex);
                                scheduleReconnect("ping failure");
                                return null;
                            });

                    // Schedule pong timeout check
                    schedulePongTimeout();
                }
            } catch (Exception e) {
                logger.error("[{}] Unexpected error while pinging", instanceId, e);
            }
        }, pingInterval.toSeconds(), pingInterval.toSeconds(), TimeUnit.SECONDS);

        logger.debug("[{}] Ping/pong mechanism started", instanceId);
    }

    /**
     * Schedules a timeout check for pong response.
     */
    private void schedulePongTimeout() {
        if (pongTimeoutTask != null) {
            pongTimeoutTask.cancel(false);
        }

        pongTimeoutTask = scheduler.schedule(() -> {
            if (connection != null) {
                Instant lastPong = connection.lastPongReceived().get();
                if (lastPong != null && Duration.between(lastPong, Instant.now()).compareTo(pongTimeout) > 0) {
                    logger.warn("[{}] Pong timeout detected for session: {}", instanceId, connection.sessionId());

                    if (lastErrorHandler != null) {
                        lastErrorHandler.accept("Connection timeout - no pong received");
                    }

                    scheduleReconnect("pong timeout");
                }
            }
        }, pongTimeout.toSeconds(), TimeUnit.SECONDS);
    }

    /**
     * Stops the ping/pong mechanism.
     */
    private void stopPingPong() {
        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
        }

        if (pongTimeoutTask != null) {
            pongTimeoutTask.cancel(false);
            pongTimeoutTask = null;
        }

        logger.debug("[{}] Ping/pong mechanism stopped", instanceId);
    }

    // ==================== Utility Methods ====================

    /**
     * Connection status enumeration
     */
    public enum ConnectionStatus {
        CONNECTED, DISCONNECTED, RECONNECTING
    }

    /**
     * WebSocket listener implementation with enhanced error handling.
     */
    private class WebSocketListener implements WebSocket.Listener {
        private final String sessionId;
        private final Consumer<TaskResponse> messageHandler;
        private final Consumer<String> errorHandler;
        private final StringBuilder partialMessage = new StringBuilder();

        public WebSocketListener(String sessionId, Consumer<TaskResponse> messageHandler,
                                 Consumer<String> errorHandler) {
            this.sessionId = sessionId;
            this.messageHandler = messageHandler;
            this.errorHandler = errorHandler;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            logger.info("[{}] WebSocket connection opened for session: {}", instanceId, sessionId);

            // Register this connection in global metrics by session ID
            if (globalMetrics != null) {
                globalMetrics.addConnection(sessionId);
            }

            webSocket.request(1); // Request more messages
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partialMessage.append(data);

            if (last) {
                String message = partialMessage.toString();
                partialMessage.setLength(0);

                if (connection != null) {
                    connection.lastActivity().set(Instant.now());
                }

                // Increment received message counter for complete messages
                if (msgReceivedTotal != null) {
                    msgReceivedTotal.increment();
                }
                if (logger.isTraceEnabled()) {
                    logger.trace("InstanceID[{}] sessionId {} , Received WebSocket message '{}'", instanceId,
                                 sessionId, message);
                }
                try {
                    TaskResponse taskResponse = objectMapper.readValue(message, TaskResponse.class);
                    if (messageHandler != null) {
                        // Execute message handler asynchronously
                        executor.execute(() -> messageHandler.accept(taskResponse));
                    }
                } catch (JsonProcessingException e) {
                    logger.error("[{}] Failed to parse WebSocket message for session {}: {}", instanceId, sessionId,
                                 message, e);
                    if (errorHandler != null) {
                        errorHandler.accept("Failed to parse message: " + e.getMessage());
                    }
                }
            }

            webSocket.request(1); // Request next message
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            logger.debug("[{}] Received binary WebSocket message ({} bytes) for session {}", instanceId,
                         data.remaining(), sessionId);
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            logger.trace("[{}] Received ping for session {}, sending pong", instanceId, sessionId);
            webSocket.sendPong(message);
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            logger.trace("[{}] Received pong for session: {}", instanceId, sessionId);
            if (connection != null) {
                Instant now = Instant.now();
                connection.lastPongReceived().set(now);
                connection.lastActivity().set(now);
            }
            // Update pong timestamp for metrics
            lastPongTimestamp.set(System.currentTimeMillis());
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            // Check if this was an expected close (after ENDNODE)
            boolean wasExpected = expectedClose.getAndSet(false);
            boolean wasIntentional = intentionalClose.get();

            logger.info("[{}] WebSocket onClose callback for session {}: status={}, reason='{}', " + "wasExpected={},"
                                + " wasIntentional={}", instanceId, sessionId, statusCode, reason, wasExpected,
                        wasIntentional);

            // Early return for expected closures - no reconnection needed
            if (wasExpected) {
                logger.info("[{}] Expected close after ENDNODE for session {} - treating as normal completion " +
                                    "(actual status: {}, reason: '{}')", instanceId, sessionId, statusCode, reason);

                // Treat as normal closure for metrics regardless of actual status code
                if (globalMetrics != null) {
                    globalMetrics.recordCloseStatus(1000); // Record as normal
                }

                // Remove from global metrics
                if (globalMetrics != null) {
                    boolean removed = globalMetrics.removeConnection(sessionId);
                    if (!removed) {
                        logger.warn("[{}] Session {} was not in global metrics during expected close", instanceId,
                                    sessionId);
                    }
                }

                // Clean up local connection state
                if (connection != null) {
                    connection.isConnected().set(false);
                    connection = null;
                }

                // Update local status
                connectionStatus.set(0);
                stopPingPong();

                // No reconnection for expected closures
                logger.debug("[{}] Skipping reconnection for expected close of session {}", instanceId, sessionId);
                return CompletableFuture.completedFuture(null);
            }

            // Handle normal or intentional closures
            if (wasIntentional || statusCode == 1000 || statusCode == 1001) {
                logger.info("[{}] Normal/intentional close for session {}: {} - {}", instanceId, sessionId,
                            statusCode, reason);

                // Record actual status for intentional closures
                if (globalMetrics != null) {
                    globalMetrics.recordCloseStatus(statusCode);
                }
            } else {
                // Abnormal closure
                logger.warn("[{}] Abnormal close for session {}: {} - {}", instanceId, sessionId, statusCode, reason);

                // Record actual status
                if (globalMetrics != null) {
                    globalMetrics.recordCloseStatus(statusCode);
                }
            }

            // Remove this session from global metrics - this is critical for proper cleanup
            if (globalMetrics != null) {
                boolean removed = globalMetrics.removeConnection(sessionId);
                if (!removed) {
                    logger.warn("[{}] Session {} was not in global metrics during onClose - possible duplicate " +
                                        "removal", instanceId, sessionId);
                }
            }

            // Clean up local connection state
            if (connection != null) {
                connection.isConnected().set(false);
                connection = null;
            }

            // Update local status
            connectionStatus.set(0);

            stopPingPong();

            // Only schedule reconnection for truly abnormal closures
            if (!wasIntentional && statusCode != 1000 && statusCode != 1001) {
                logger.warn("[{}] Scheduling reconnection for abnormal closure of session {}: {} - {}", instanceId,
                            sessionId, statusCode, reason);

                if (lastErrorHandler != null) {
                    lastErrorHandler.accept(String.format("Connection closed abnormally: %d - %s", statusCode, reason));
                }
                scheduleReconnect(String.format("abnormal closure %d", statusCode));
            } else {
                logger.debug("[{}] No reconnection needed for session {} (intentional={}, status={})", instanceId,
                             sessionId, wasIntentional, statusCode);
            }

            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            logger.error("[{}] WebSocket error occurred for session: {}", instanceId, sessionId, error);

            if (errorHandler != null) {
                errorHandler.accept("WebSocket error: " + error.getMessage());
            }

            // Remove this session from global metrics - ensure cleanup happens
            if (globalMetrics != null) {
                boolean removed = globalMetrics.removeConnection(sessionId);
                if (!removed) {
                    logger.warn("[{}] Session {} was not in global metrics during onError - possible duplicate " +
                                        "removal", instanceId, sessionId);
                }
            }

            // Clean up local connection state
            if (connection != null) {
                connection.isConnected().set(false);
                connection = null;
            }

            // Update local status
            connectionStatus.set(0);

            stopPingPong();

            if (!intentionalClose.get()) {
                scheduleReconnect("websocket error");
            }
        }
    }
}