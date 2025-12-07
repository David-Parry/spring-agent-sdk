/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
class LocalRetryHandlerTest {
    
    @Mock
    private LocalQueueService queueService;
    
    @Mock
    private LocalQueueProperties properties;
    
    private LocalRetryHandler retryHandler;
    
    @BeforeEach
    void setUp() {
        lenient().when(properties.getRetryAttempts()).thenReturn(3);
        lenient().when(properties.getRetryDelayMs()).thenReturn(100L);
        lenient().when(properties.getMaxRetryDelayMs()).thenReturn(1000L);
        lenient().when(properties.isExponentialBackoff()).thenReturn(true);

        retryHandler = new LocalRetryHandler(queueService, properties);
    }
    
    @Test
    void testHandleFailedMessageWithinRetryLimit() {
        String queueName = "test-queue";
        String message = "test message";
        Exception exception = new RuntimeException("Test error");

        // First failure - should retry
        boolean requeued = retryHandler.handleFailedMessage(queueName, message, exception);
        assertTrue(requeued, "Message should be requeued for retry");

        // Verify retry attempt is tracked
        assertEquals(1, retryHandler.getRetryAttempts(message));
    }
    
    @Test
    void testHandleFailedMessageExceedsRetryLimit() {
        String queueName = "test-queue";
        String message = "test message";
        Exception exception = new RuntimeException("Test error");
        
        when(queueService.enqueue(anyString(), anyString())).thenReturn(true);
        
        // Simulate multiple failures
        for (int i = 0; i < 3; i++) {
            retryHandler.handleFailedMessage(queueName, message, exception);
        }
        
        // Fourth failure should move to DLQ
        boolean requeued = retryHandler.handleFailedMessage(queueName, message, exception);
        assertFalse(requeued, "Message should not be requeued after max retries");
        
        // Verify message was moved to DLQ
        verify(queueService, atLeastOnce()).enqueue(eq(queueName + ".DLQ"), anyString());
    }
    
    @Test
    void testClearRetryTracking() {
        String message = "test message";
        Exception exception = new RuntimeException("Test error");
        
        // Fail once
        retryHandler.handleFailedMessage("queue", message, exception);
        assertEquals(1, retryHandler.getRetryAttempts(message));
        
        // Clear tracking
        retryHandler.clearRetryTracking(message);
        assertEquals(0, retryHandler.getRetryAttempts(message));
    }
    
    @Test
    void testGetRetryAttemptsForNewMessage() {
        String message = "new message";
        assertEquals(0, retryHandler.getRetryAttempts(message));
    }
}
