/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.service;

import java.net.http.WebSocket;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Record that encapsulates a WebSocket connection with its associated session state.
 * Each instance represents a unique session with isolated state to prevent session swapping.
 * 
 * @param sessionId The unique session identifier
 * @param requestId The unique request identifier
 * @param webSocket The underlying WebSocket connection
 * @param isConnected Atomic boolean tracking connection status
 * @param createdAt The timestamp when this connection was created
 * @param lastActivity Atomic reference to the last activity timestamp
 * @param lastPongReceived Atomic reference to the last pong received timestamp
 */
public record WebSocketConnection(
    String sessionId,
    String requestId,
    WebSocket webSocket,
    AtomicBoolean isConnected,
    Instant createdAt,
    AtomicReference<Instant> lastActivity,
    AtomicReference<Instant> lastPongReceived
) {
    /**
     * Compact constructor that initializes mutable state with current timestamp.
     */
    public WebSocketConnection(String sessionId, String requestId, WebSocket webSocket) {
        this(
            sessionId,
            requestId,
            webSocket,
            new AtomicBoolean(true),
            Instant.now(),
            new AtomicReference<>(Instant.now()),
            new AtomicReference<>(Instant.now())
        );
    }

}
