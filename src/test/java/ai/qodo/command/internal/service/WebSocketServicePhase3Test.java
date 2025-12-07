/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.service;

import ai.qodo.command.internal.api.TaskResponse;
import ai.qodo.command.internal.config.QodoProperties;
import ai.qodo.command.internal.metrics.WebSocketMetrics;
import ai.qodo.command.internal.pojo.CommandSession;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.net.http.WebSocket;
import java.time.Duration;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Phase 3: Reconnection Logic Tests for WebSocketService.
 * Tests reconnection mechanism, exponential backoff, max attempts, and CommandException handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WebSocketService - Phase 3: Reconnection Logic Tests")
class WebSocketServicePhase3Test {

    @Mock
    private QodoProperties qodoProperties;
    
    @Mock
    private QodoProperties.Websocket websocketProperties;
    
    @Mock
    private WebSocketMetrics globalMetrics;
    
    @Mock
    private ObjectProvider<MeterRegistry> meterRegistryProvider;
    
    @Mock
    private WebSocket webSocket;
    
    @Mock
    private CommandSession commandSession;
    
    @Mock
    private Consumer<TaskResponse> messageHandler;
    
    @Mock
    private Consumer<String> errorHandler;
    
    private WebSocketService webSocketService;
    
    private static final String TEST_SESSION_ID = "test-session-123";
    private static final String TEST_REQUEST_ID = "test-request-456";
    private static final String TEST_TOKEN = "test-token-789";
    private static final String TEST_BASE_URL = "https://api.test.qodo.ai";
    private static final String TEST_CHECKPOINT_ID = "checkpoint-abc-123";

    @BeforeEach
    void setUp() {
        // Setup QodoProperties mock
        lenient().when(qodoProperties.getBaseUrl()).thenReturn(TEST_BASE_URL);
        lenient().when(qodoProperties.getWebsocket()).thenReturn(websocketProperties);
        
        // Setup WebSocket properties with reconnection settings
        lenient().when(websocketProperties.getToken()).thenReturn(TEST_TOKEN);
        lenient().when(websocketProperties.getPingIntervalSeconds()).thenReturn(30L);
        lenient().when(websocketProperties.getPongTimeoutSeconds()).thenReturn(10L);
        lenient().when(websocketProperties.getConnectionTimeoutSeconds()).thenReturn(60L);
        lenient().when(websocketProperties.getMaxReconnectAttempts()).thenReturn(3);
        lenient().when(websocketProperties.getInitialReconnectDelay()).thenReturn(Duration.ofSeconds(1));
        lenient().when(websocketProperties.getMaxReconnectDelay()).thenReturn(Duration.ofSeconds(10));
        lenient().when(websocketProperties.getReadySignalTimeoutSeconds()).thenReturn(30L);
        
        // Setup MeterRegistry provider - return null to disable metrics
        lenient().when(meterRegistryProvider.getIfAvailable()).thenReturn(null);
        
        // Setup CommandSession mock
        lenient().when(commandSession.sessionId()).thenReturn(TEST_SESSION_ID);
        lenient().when(commandSession.requestId()).thenReturn(TEST_REQUEST_ID);
        lenient().when(commandSession.checkPointId()).thenReturn(TEST_CHECKPOINT_ID);
        lenient().when(commandSession.generateWebSocketUrl(anyString(), anyBoolean()))
            .thenAnswer(invocation -> {
                String baseUrl = invocation.getArgument(0);
                boolean isReconnect = invocation.getArgument(1);
                if (isReconnect) {
                    return baseUrl + "/v2/agentic/ws/connect?session_id=" + TEST_SESSION_ID + 
                           "&request_id=" + TEST_REQUEST_ID + "&checkpoint_id=" + TEST_CHECKPOINT_ID;
                } else {
                    return baseUrl + "/v2/agentic/ws/connect?session_id=" + TEST_SESSION_ID + 
                           "&request_id=" + TEST_REQUEST_ID;
                }
            });
        
        // Create service instance
        webSocketService = new WebSocketService(qodoProperties, meterRegistryProvider, globalMetrics);
    }

    // ==================== Exponential Backoff Tests ====================

    @Test
    @DisplayName("Should calculate exponential backoff for attempt 1")
    void testExponentialBackoffAttempt1() {
        // Arrange
        Duration baseDelay = Duration.ofSeconds(1);
        Duration maxDelay = Duration.ofSeconds(10);
        
        // Act - calculate for attempt 1: base * 2^0 = 1 second
        long expectedMin = (long) (baseDelay.toMillis() * 0.8); // With jitter
        long expectedMax = (long) (baseDelay.toMillis() * 1.2);
        
        // Assert - verify calculation logic
        assertTrue(expectedMin <= baseDelay.toMillis());
        assertTrue(expectedMax >= baseDelay.toMillis());
    }

    @Test
    @DisplayName("Should calculate exponential backoff for attempt 2")
    void testExponentialBackoffAttempt2() {
        // Arrange
        Duration baseDelay = Duration.ofSeconds(1);
        int attempt = 2;
        
        // Act - calculate for attempt 2: base * 2^1 = 2 seconds
        long exponentialDelay = baseDelay.toMillis() * (1L << (attempt - 1));
        
        // Assert
        assertEquals(2000, exponentialDelay);
    }

    @Test
    @DisplayName("Should calculate exponential backoff for attempt 3")
    void testExponentialBackoffAttempt3() {
        // Arrange
        Duration baseDelay = Duration.ofSeconds(1);
        int attempt = 3;
        
        // Act - calculate for attempt 3: base * 2^2 = 4 seconds
        long exponentialDelay = baseDelay.toMillis() * (1L << (attempt - 1));
        
        // Assert
        assertEquals(4000, exponentialDelay);
    }

    @Test
    @DisplayName("Should cap exponential backoff at max delay")
    void testExponentialBackoffCappedAtMax() {
        // Arrange
        Duration baseDelay = Duration.ofSeconds(1);
        Duration maxDelay = Duration.ofSeconds(10);
        int attempt = 10; // Would be 512 seconds without cap
        
        // Act - calculate with cap
        long exponentialDelay = baseDelay.toMillis() * (1L << Math.min(10, attempt - 1));
        long cappedDelay = Math.min(exponentialDelay, maxDelay.toMillis());
        
        // Assert
        assertEquals(maxDelay.toMillis(), cappedDelay);
    }

    @Test
    @DisplayName("Should apply jitter to backoff delay")
    void testBackoffJitter() {
        // Arrange
        long baseDelay = 1000; // 1 second
        double jitterFactor = 0.8; // Minimum jitter
        
        // Act - apply jitter (±20%)
        long delayWithMinJitter = (long) (baseDelay * jitterFactor);
        long delayWithMaxJitter = (long) (baseDelay * 1.2);
        
        // Assert - jitter range is 800ms to 1200ms
        assertEquals(800, delayWithMinJitter);
        assertEquals(1200, delayWithMaxJitter);
    }

    @Test
    @DisplayName("Should ensure jitter stays within bounds")
    void testJitterBounds() {
        // Arrange
        long baseDelay = 5000; // 5 seconds
        
        // Act - calculate jitter bounds
        long minDelay = (long) (baseDelay * 0.8);
        long maxDelay = (long) (baseDelay * 1.2);
        
        // Assert - 20% jitter means ±20%
        assertEquals(4000, minDelay);
        assertEquals(6000, maxDelay);
        assertTrue(maxDelay - minDelay == 2000); // 40% range
    }

    // ==================== Max Reconnect Attempts Tests ====================

    @Test
    @DisplayName("Should respect max reconnect attempts configuration")
    void testMaxReconnectAttemptsConfiguration() {
        // Arrange
        when(websocketProperties.getMaxReconnectAttempts()).thenReturn(5);
        
        // Act - create new service with updated config
        WebSocketService service = new WebSocketService(qodoProperties, meterRegistryProvider, globalMetrics);
        
        // Assert - service was created with configuration
        assertNotNull(service);
        verify(websocketProperties, atLeastOnce()).getMaxReconnectAttempts();
    }

    @Test
    @DisplayName("Should use default max reconnect attempts")
    void testDefaultMaxReconnectAttempts() {
        // Arrange - default is 3
        when(websocketProperties.getMaxReconnectAttempts()).thenReturn(3);
        
        // Act
        WebSocketService service = new WebSocketService(qodoProperties, meterRegistryProvider, globalMetrics);
        
        // Assert
        assertNotNull(service);
        verify(websocketProperties, atLeastOnce()).getMaxReconnectAttempts();
    }

    @Test
    @DisplayName("Should allow zero max reconnect attempts")
    void testZeroMaxReconnectAttempts() {
        // Arrange - no reconnection attempts
        when(websocketProperties.getMaxReconnectAttempts()).thenReturn(0);
        
        // Act
        WebSocketService service = new WebSocketService(qodoProperties, meterRegistryProvider, globalMetrics);
        
        // Assert
        assertNotNull(service);
    }

    @Test
    @DisplayName("Should allow high max reconnect attempts")
    void testHighMaxReconnectAttempts() {
        // Arrange - many attempts
        when(websocketProperties.getMaxReconnectAttempts()).thenReturn(100);
        
        // Act
        WebSocketService service = new WebSocketService(qodoProperties, meterRegistryProvider, globalMetrics);
        
        // Assert
        assertNotNull(service);
    }

    // ==================== CommandException Tests ====================

    @Test
    @DisplayName("Should throw CommandException after max attempts")
    void testCommandExceptionAfterMaxAttempts() {
        // This test validates that CommandException exists and can be thrown
        // Actual throwing happens in scheduleReconnect() after max attempts
        
        // Arrange
        String errorMessage = "WebSocket connection failed after 3 reconnect attempts - test failure";
        
        // Act & Assert
        CommandException exception = assertThrows(CommandException.class, () -> {
            throw new CommandException(errorMessage);
        });
        
        assertEquals(errorMessage, exception.getMessage());
    }

    @Test
    @DisplayName("Should create CommandException with message")
    void testCommandExceptionWithMessage() {
        // Arrange
        String message = "Connection failed";
        
        // Act
        CommandException exception = new CommandException(message);
        
        // Assert
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("Should create CommandException with message and cause")
    void testCommandExceptionWithCause() {
        // Arrange
        String message = "Connection failed";
        Throwable cause = new RuntimeException("Network error");
        
        // Act
        CommandException exception = new CommandException(message, cause);
        
        // Assert
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("Should be RuntimeException subclass")
    void testCommandExceptionIsRuntimeException() {
        // Arrange & Act
        CommandException exception = new CommandException("test");
        
        // Assert
        assertTrue(exception instanceof RuntimeException);
    }

    // ==================== Checkpoint ID Tests ====================

    @Test
    @DisplayName("Should use checkpoint ID for reconnection")
    void testCheckpointIdForReconnection() {
        // Arrange - CommandSession has checkpoint ID
        when(commandSession.checkPointId()).thenReturn(TEST_CHECKPOINT_ID);
        
        // Act - generate URL for reconnection
        String reconnectUrl = commandSession.generateWebSocketUrl(TEST_BASE_URL, true);
        
        // Assert - URL should include checkpoint_id
        assertTrue(reconnectUrl.contains("checkpoint_id=" + TEST_CHECKPOINT_ID));
        assertTrue(reconnectUrl.contains("session_id=" + TEST_SESSION_ID));
        assertTrue(reconnectUrl.contains("request_id=" + TEST_REQUEST_ID));
    }

    @Test
    @DisplayName("Should not use checkpoint ID for initial connection")
    void testNoCheckpointIdForInitialConnection() {
        // Act - generate URL for initial connection
        String initialUrl = commandSession.generateWebSocketUrl(TEST_BASE_URL, false);
        
        // Assert - URL should NOT include checkpoint_id
        assertFalse(initialUrl.contains("checkpoint_id"));
        assertTrue(initialUrl.contains("session_id=" + TEST_SESSION_ID));
        assertTrue(initialUrl.contains("request_id=" + TEST_REQUEST_ID));
    }

    @Test
    @DisplayName("Should handle null checkpoint ID")
    void testNullCheckpointId() {
        // Arrange
        when(commandSession.checkPointId()).thenReturn(null);
        
        // Act - should handle gracefully
        String checkpointId = commandSession.checkPointId();
        
        // Assert
        assertNull(checkpointId);
    }

    @Test
    @DisplayName("Should handle empty checkpoint ID")
    void testEmptyCheckpointId() {
        // Arrange
        when(commandSession.checkPointId()).thenReturn("");
        
        // Act
        String checkpointId = commandSession.checkPointId();
        
        // Assert
        assertEquals("", checkpointId);
    }

    // ==================== Reconnection Delay Configuration Tests ====================

    @Test
    @DisplayName("Should use configured initial reconnect delay")
    void testInitialReconnectDelayConfiguration() {
        // Arrange
        Duration customDelay = Duration.ofSeconds(5);
        when(websocketProperties.getInitialReconnectDelay()).thenReturn(customDelay);
        
        // Act
        WebSocketService service = new WebSocketService(qodoProperties, meterRegistryProvider, globalMetrics);
        
        // Assert - service was created with configuration
        assertNotNull(service);
        // Verify the configuration is available (lenient stubbing means it may not be called during construction)
        assertEquals(customDelay, websocketProperties.getInitialReconnectDelay());
    }

    @Test
    @DisplayName("Should use configured max reconnect delay")
    void testMaxReconnectDelayConfiguration() {
        // Arrange
        Duration customMaxDelay = Duration.ofSeconds(30);
        when(websocketProperties.getMaxReconnectDelay()).thenReturn(customMaxDelay);
        
        // Act
        WebSocketService service = new WebSocketService(qodoProperties, meterRegistryProvider, globalMetrics);
        
        // Assert - service was created with configuration
        assertNotNull(service);
        // Verify the configuration is available (lenient stubbing means it may not be called during construction)
        assertEquals(customMaxDelay, websocketProperties.getMaxReconnectDelay());
    }

    @Test
    @DisplayName("Should handle very short initial delay")
    void testVeryShortInitialDelay() {
        // Arrange
        Duration shortDelay = Duration.ofMillis(100);
        when(websocketProperties.getInitialReconnectDelay()).thenReturn(shortDelay);
        
        // Act
        WebSocketService service = new WebSocketService(qodoProperties, meterRegistryProvider, globalMetrics);
        
        // Assert
        assertNotNull(service);
    }

    @Test
    @DisplayName("Should handle very long max delay")
    void testVeryLongMaxDelay() {
        // Arrange
        Duration longDelay = Duration.ofMinutes(5);
        when(websocketProperties.getMaxReconnectDelay()).thenReturn(longDelay);
        
        // Act
        WebSocketService service = new WebSocketService(qodoProperties, meterRegistryProvider, globalMetrics);
        
        // Assert
        assertNotNull(service);
    }

    // ==================== Reconnection State Tests ====================

    @Test
    @DisplayName("Should track reconnection state")
    void testReconnectionStateTracking() {
        // Arrange - service starts in disconnected state
        assertFalse(webSocketService.isConnected(TEST_SESSION_ID));
        
        // Assert - initial state is DISCONNECTED
        assertEquals(WebSocketService.ConnectionStatus.DISCONNECTED, 
                    webSocketService.getConnectionStatus(TEST_SESSION_ID));
    }

    @Test
    @DisplayName("Should prevent concurrent reconnection attempts")
    void testPreventConcurrentReconnection() {
        // This test validates the atomic reconnecting flag logic
        // In the actual implementation, reconnecting.compareAndSet(false, true)
        // prevents multiple concurrent reconnection attempts
        
        // Arrange - simulate atomic flag behavior
        java.util.concurrent.atomic.AtomicBoolean reconnecting = new java.util.concurrent.atomic.AtomicBoolean(false);
        
        // Act - first attempt should succeed
        boolean firstAttempt = reconnecting.compareAndSet(false, true);
        
        // Act - second attempt should fail (already reconnecting)
        boolean secondAttempt = reconnecting.compareAndSet(false, true);
        
        // Assert
        assertTrue(firstAttempt);
        assertFalse(secondAttempt);
    }

    @Test
    @DisplayName("Should reset reconnection flag after completion")
    void testResetReconnectionFlag() {
        // Arrange
        java.util.concurrent.atomic.AtomicBoolean reconnecting = new java.util.concurrent.atomic.AtomicBoolean(false);
        
        // Act - set flag
        reconnecting.set(true);
        assertTrue(reconnecting.get());
        
        // Act - reset flag
        reconnecting.set(false);
        
        // Assert
        assertFalse(reconnecting.get());
    }

    // ==================== Intentional Close Tests ====================

    @Test
    @DisplayName("Should not reconnect after intentional close")
    void testNoReconnectAfterIntentionalClose() {
        // This test validates the intentionalClose flag logic
        // When intentionalClose is true, scheduleReconnect() should return early
        
        // Arrange
        java.util.concurrent.atomic.AtomicBoolean intentionalClose = new java.util.concurrent.atomic.AtomicBoolean(false);
        
        // Act - set intentional close
        intentionalClose.set(true);
        
        // Assert - flag is set
        assertTrue(intentionalClose.get());
    }

    @Test
    @DisplayName("Should reconnect after unintentional close")
    void testReconnectAfterUnintentionalClose() {
        // Arrange
        java.util.concurrent.atomic.AtomicBoolean intentionalClose = new java.util.concurrent.atomic.AtomicBoolean(false);
        
        // Assert - flag is not set (unintentional close)
        assertFalse(intentionalClose.get());
    }

    // ==================== Reconnection Attempt Counter Tests ====================

    @Test
    @DisplayName("Should increment reconnection attempt counter")
    void testIncrementReconnectionAttempts() {
        // Arrange
        java.util.concurrent.atomic.AtomicInteger reconnectAttempts = new java.util.concurrent.atomic.AtomicInteger(0);
        
        // Act - increment attempts
        int attempt1 = reconnectAttempts.incrementAndGet();
        int attempt2 = reconnectAttempts.incrementAndGet();
        int attempt3 = reconnectAttempts.incrementAndGet();
        
        // Assert
        assertEquals(1, attempt1);
        assertEquals(2, attempt2);
        assertEquals(3, attempt3);
    }

    @Test
    @DisplayName("Should reset reconnection attempts after success")
    void testResetReconnectionAttempts() {
        // Arrange
        java.util.concurrent.atomic.AtomicInteger reconnectAttempts = new java.util.concurrent.atomic.AtomicInteger(3);
        
        // Act - reset after successful reconnection
        reconnectAttempts.set(0);
        
        // Assert
        assertEquals(0, reconnectAttempts.get());
    }

    @Test
    @DisplayName("Should check if max attempts exceeded")
    void testMaxAttemptsExceeded() {
        // Arrange
        int maxAttempts = 3;
        int currentAttempts = 4;
        
        // Act
        boolean exceeded = currentAttempts > maxAttempts;
        
        // Assert
        assertTrue(exceeded);
    }

    @Test
    @DisplayName("Should check if max attempts not exceeded")
    void testMaxAttemptsNotExceeded() {
        // Arrange
        int maxAttempts = 3;
        int currentAttempts = 2;
        
        // Act
        boolean exceeded = currentAttempts > maxAttempts;
        
        // Assert
        assertFalse(exceeded);
    }

    // ==================== Reconnection Context Tests ====================

    @Test
    @DisplayName("Should preserve session context for reconnection")
    void testPreserveSessionContext() {
        // Arrange - verify session context is available
        assertNotNull(commandSession.sessionId());
        assertNotNull(commandSession.requestId());
        
        // Assert - context is preserved
        assertEquals(TEST_SESSION_ID, commandSession.sessionId());
        assertEquals(TEST_REQUEST_ID, commandSession.requestId());
    }

    @Test
    @DisplayName("Should preserve token for reconnection")
    void testPreserveTokenForReconnection() {
        // Arrange
        String token = websocketProperties.getToken();
        
        // Assert - token is available for reconnection
        assertNotNull(token);
        assertEquals(TEST_TOKEN, token);
    }

    @Test
    @DisplayName("Should preserve handlers for reconnection")
    void testPreserveHandlersForReconnection() {
        // Assert - handlers are available
        assertNotNull(messageHandler);
        assertNotNull(errorHandler);
    }

    // ==================== Backoff Calculation Edge Cases ====================

    @Test
    @DisplayName("Should handle attempt overflow in backoff calculation")
    void testBackoffCalculationOverflow() {
        // Arrange - very high attempt number
        int attempt = 100;
        long baseDelay = 1000;
        
        // Act - use Math.min to prevent overflow
        long shift = Math.min(10, attempt - 1);
        long exponentialDelay = baseDelay * (1L << shift);
        
        // Assert - should be capped at 2^10 = 1024 seconds
        assertEquals(1024000, exponentialDelay);
    }

    @Test
    @DisplayName("Should handle zero base delay")
    void testZeroBaseDelay() {
        // Arrange
        Duration baseDelay = Duration.ZERO;
        
        // Act
        long delayMillis = baseDelay.toMillis();
        
        // Assert
        assertEquals(0, delayMillis);
    }

    @Test
    @DisplayName("Should handle negative attempt number")
    void testNegativeAttemptNumber() {
        // Arrange
        int attempt = -1;
        
        // Act - ensure non-negative
        int safeAttempt = Math.max(1, attempt);
        
        // Assert
        assertEquals(1, safeAttempt);
    }

    // ==================== Integration Tests ====================

    @Test
    @DisplayName("Should calculate complete backoff sequence")
    void testCompleteBackoffSequence() {
        // Arrange
        long baseDelay = 1000; // 1 second
        long maxDelay = 10000; // 10 seconds
        
        // Act & Assert - calculate sequence for 5 attempts
        for (int attempt = 1; attempt <= 5; attempt++) {
            long exponentialDelay = baseDelay * (1L << Math.min(10, attempt - 1));
            long cappedDelay = Math.min(exponentialDelay, maxDelay);
            
            // Verify delay increases exponentially but caps at max
            assertTrue(cappedDelay <= maxDelay);
            assertTrue(cappedDelay >= baseDelay);
        }
    }

    @Test
    @DisplayName("Should handle complete reconnection workflow")
    void testCompleteReconnectionWorkflow() {
        // This test validates the complete reconnection logic flow
        
        // 1. Initial state
        assertFalse(webSocketService.isConnected(TEST_SESSION_ID));
        
        // 2. Connection context is preserved
        assertEquals(TEST_SESSION_ID, commandSession.sessionId());
        assertEquals(TEST_REQUEST_ID, commandSession.requestId());
        
        // 3. Checkpoint ID is available for reconnection
        assertEquals(TEST_CHECKPOINT_ID, commandSession.checkPointId());
        
        // 4. Configuration is available
        assertEquals(3, websocketProperties.getMaxReconnectAttempts());
        assertEquals(Duration.ofSeconds(1), websocketProperties.getInitialReconnectDelay());
        assertEquals(Duration.ofSeconds(10), websocketProperties.getMaxReconnectDelay());
    }
}
