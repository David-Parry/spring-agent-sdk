/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit breaker pattern implementation for WebSocket connections.
 * Prevents cascading failures by temporarily blocking connection attempts
 * after a threshold of consecutive failures is reached.
 * 
 * <p>States:
 * <ul>
 *   <li>CLOSED: Normal operation, connections allowed</li>
 *   <li>OPEN: Too many failures, connections blocked</li>
 *   <li>HALF_OPEN: Testing if service recovered, limited connections allowed</li>
 * </ul>
 */
public class WebSocketCircuitBreaker {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketCircuitBreaker.class);
    
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicReference<Instant> lastFailureTime = new AtomicReference<>();
    private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
    
    private final int failureThreshold;
    private final Duration cooldownPeriod;
    private final int halfOpenSuccessThreshold;
    
    /**
     * Circuit breaker states
     */
    public enum CircuitState {
        CLOSED,      // Normal operation
        OPEN,        // Circuit is open, blocking requests
        HALF_OPEN    // Testing if service recovered
    }
    
    /**
     * Creates a circuit breaker with default settings.
     * - Failure threshold: 5 consecutive failures
     * - Cooldown period: 5 minutes
     * - Half-open success threshold: 2 successful attempts to close circuit
     */
    public WebSocketCircuitBreaker() {
        this(5, Duration.ofMinutes(5), 2);
    }
    
    /**
     * Creates a circuit breaker with custom settings.
     * 
     * @param failureThreshold Number of consecutive failures before opening circuit
     * @param cooldownPeriod Time to wait before attempting to close circuit
     * @param halfOpenSuccessThreshold Number of successes needed in half-open state to close circuit
     */
    public WebSocketCircuitBreaker(int failureThreshold, Duration cooldownPeriod, int halfOpenSuccessThreshold) {
        this.failureThreshold = failureThreshold;
        this.cooldownPeriod = cooldownPeriod;
        this.halfOpenSuccessThreshold = halfOpenSuccessThreshold;
        
        logger.info("WebSocketCircuitBreaker initialized: failureThreshold={}, cooldownPeriod={}min, " +
                   "halfOpenSuccessThreshold={}", 
                   failureThreshold, cooldownPeriod.toMinutes(), halfOpenSuccessThreshold);
    }
    
    /**
     * Checks if a connection attempt should be allowed.
     * 
     * @return true if connection should be attempted, false if circuit is open
     */
    public boolean shouldAttemptConnection() {
        CircuitState currentState = state.get();
        
        switch (currentState) {
            case CLOSED:
                // Normal operation, allow connection
                return true;
                
            case OPEN:
                // Check if cooldown period has elapsed
                Instant lastFailure = lastFailureTime.get();
                if (lastFailure != null) {
                    Duration timeSinceLastFailure = Duration.between(lastFailure, Instant.now());
                    if (timeSinceLastFailure.compareTo(cooldownPeriod) >= 0) {
                        // Cooldown period elapsed, transition to half-open
                        if (state.compareAndSet(CircuitState.OPEN, CircuitState.HALF_OPEN)) {
                            logger.info("Circuit breaker transitioning from OPEN to HALF_OPEN after {}min cooldown",
                                       timeSinceLastFailure.toMinutes());
                            successCount.set(0);
                            return true;
                        }
                    } else {
                        logger.warn("Circuit breaker is OPEN - blocking connection attempt " +
                                   "({}min remaining in cooldown, {} failures recorded)",
                                   cooldownPeriod.minus(timeSinceLastFailure).toMinutes(),
                                   failureCount.get());
                        return false;
                    }
                }
                return false;
                
            case HALF_OPEN:
                // Allow limited connection attempts to test if service recovered
                logger.debug("Circuit breaker is HALF_OPEN - allowing test connection attempt");
                return true;
                
            default:
                return true;
        }
    }
    
    /**
     * Records a successful connection.
     * In HALF_OPEN state, may transition to CLOSED if enough successes recorded.
     */
    public void recordSuccess() {
        CircuitState currentState = state.get();
        
        switch (currentState) {
            case CLOSED:
                // Reset failure count on success
                failureCount.set(0);
                logger.debug("Circuit breaker: Success recorded in CLOSED state");
                break;
                
            case HALF_OPEN:
                int successes = successCount.incrementAndGet();
                logger.info("Circuit breaker: Success recorded in HALF_OPEN state ({}/{})",
                           successes, halfOpenSuccessThreshold);
                
                if (successes >= halfOpenSuccessThreshold) {
                    // Enough successes, close the circuit
                    if (state.compareAndSet(CircuitState.HALF_OPEN, CircuitState.CLOSED)) {
                        logger.info("Circuit breaker transitioning from HALF_OPEN to CLOSED after {} successes",
                                   successes);
                        failureCount.set(0);
                        successCount.set(0);
                    }
                }
                break;
                
            case OPEN:
                // Shouldn't happen, but log it
                logger.warn("Circuit breaker: Success recorded in OPEN state (unexpected)");
                break;
        }
    }
    
    /**
     * Records a connection failure.
     * May transition to OPEN state if failure threshold is reached.
     */
    public void recordFailure() {
        CircuitState currentState = state.get();
        Instant now = Instant.now();
        lastFailureTime.set(now);
        
        switch (currentState) {
            case CLOSED:
                int failures = failureCount.incrementAndGet();
                logger.warn("Circuit breaker: Failure recorded in CLOSED state ({}/{})",
                           failures, failureThreshold);
                
                if (failures >= failureThreshold) {
                    // Too many failures, open the circuit
                    if (state.compareAndSet(CircuitState.CLOSED, CircuitState.OPEN)) {
                        logger.error("Circuit breaker transitioning from CLOSED to OPEN after {} failures " +
                                    "(cooldown period: {}min)",
                                    failures, cooldownPeriod.toMinutes());
                    }
                }
                break;
                
            case HALF_OPEN:
                // Failure in half-open state, reopen circuit
                if (state.compareAndSet(CircuitState.HALF_OPEN, CircuitState.OPEN)) {
                    logger.error("Circuit breaker transitioning from HALF_OPEN to OPEN after test failure " +
                                "(cooldown period: {}min)", cooldownPeriod.toMinutes());
                    successCount.set(0);
                }
                break;
                
            case OPEN:
                // Already open, just log
                logger.debug("Circuit breaker: Failure recorded in OPEN state");
                break;
        }
    }
    
    /**
     * Gets the current state of the circuit breaker.
     * 
     * @return Current circuit state
     */
    public CircuitState getState() {
        return state.get();
    }
    
    /**
     * Gets the current failure count.
     * 
     * @return Number of consecutive failures
     */
    public int getFailureCount() {
        return failureCount.get();
    }
    
    /**
     * Gets the time of the last failure.
     * 
     * @return Instant of last failure, or null if no failures recorded
     */
    public Instant getLastFailureTime() {
        return lastFailureTime.get();
    }
    
    /**
     * Resets the circuit breaker to CLOSED state.
     * Useful for manual intervention or testing.
     */
    public void reset() {
        state.set(CircuitState.CLOSED);
        failureCount.set(0);
        successCount.set(0);
        lastFailureTime.set(null);
        logger.info("Circuit breaker manually reset to CLOSED state");
    }
    
    /**
     * Gets a human-readable status message.
     * 
     * @return Status description
     */
    public String getStatusMessage() {
        CircuitState currentState = state.get();
        int failures = failureCount.get();
        Instant lastFailure = lastFailureTime.get();
        
        switch (currentState) {
            case CLOSED:
                return String.format("Circuit CLOSED - %d failures recorded", failures);
                
            case OPEN:
                if (lastFailure != null) {
                    Duration timeSinceFailure = Duration.between(lastFailure, Instant.now());
                    Duration remaining = cooldownPeriod.minus(timeSinceFailure);
                    return String.format("Circuit OPEN - %d failures, %dmin remaining in cooldown",
                                       failures, Math.max(0, remaining.toMinutes()));
                }
                return String.format("Circuit OPEN - %d failures", failures);
                
            case HALF_OPEN:
                int successes = successCount.get();
                return String.format("Circuit HALF_OPEN - testing recovery (%d/%d successes)",
                                   successes, halfOpenSuccessThreshold);
                
            default:
                return "Circuit state unknown";
        }
    }
}
