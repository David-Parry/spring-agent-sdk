/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal;

/**
 * Lifecycle states for the WebSocket service to prevent premature destruction.
 * 
 * <p>These states track the connection lifecycle and are used to prevent
 * Spring's {@code @PreDestroy} from prematurely closing active WebSocket connections
 * in prototype-scoped beans.
 * 
 * <p>State transitions:
 * <pre>
 * CREATED → CONNECTING → CONNECTED → ACTIVE → CLOSING → CLOSED
 *              ↓            ↓
 *           CREATED      CREATED (on failure)
 * </pre>
 */
public enum LifecycleState {
    /**
     * Bean created but not yet connecting.
     * Initial state when the service is instantiated.
     */
    CREATED,
    
    /**
     * Connection attempt in progress.
     * WebSocket connection is being established.
     */
    CONNECTING,
    
    /**
     * Connection established, waiting for READY signal.
     * WebSocket is connected but application-level handshake not complete.
     */
    CONNECTED,
    
    /**
     * Actively processing messages.
     * Connection is fully established and processing application messages.
     */
    ACTIVE,
    
    /**
     * Intentionally closing.
     * Graceful shutdown in progress.
     */
    CLOSING,
    
    /**
     * Fully closed and cleaned up.
     * All resources released, connection terminated.
     */
    CLOSED
}
