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

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalQueueServiceTest {
    
    @Mock
    private LocalQueueProperties properties;
    
    private LocalQueueService queueService;
    
    @BeforeEach
    void setUp() {
        when(properties.getQueueCapacity()).thenReturn(10);
        queueService = new LocalQueueService(properties);
    }
    
    @Test
    void testEnqueueAndDequeue() throws InterruptedException {
        String queueName = "test-queue";
        String message = "test message";
        
        // Enqueue message
        boolean enqueued = queueService.enqueue(queueName, message);
        assertTrue(enqueued, "Message should be enqueued successfully");
        
        // Verify queue size
        assertEquals(1, queueService.getQueueSize(queueName));
        
        // Dequeue message
        String dequeued = queueService.dequeue(queueName, 1, TimeUnit.SECONDS);
        assertEquals(message, dequeued, "Dequeued message should match enqueued message");
        
        // Verify queue is empty
        assertEquals(0, queueService.getQueueSize(queueName));
    }
    
    @Test
    void testMultipleMessages() throws InterruptedException {
        String queueName = "test-queue";
        
        // Enqueue multiple messages
        for (int i = 0; i < 5; i++) {
            boolean enqueued = queueService.enqueue(queueName, "message-" + i);
            assertTrue(enqueued);
        }
        
        // Verify queue size
        assertEquals(5, queueService.getQueueSize(queueName));
        
        // Dequeue all messages
        for (int i = 0; i < 5; i++) {
            String message = queueService.dequeue(queueName, 1, TimeUnit.SECONDS);
            assertEquals("message-" + i, message);
        }
        
        // Verify queue is empty
        assertEquals(0, queueService.getQueueSize(queueName));
    }
    
    @Test
    void testQueueCapacity() {
        String queueName = "test-queue";
        
        // Fill queue to capacity
        for (int i = 0; i < 10; i++) {
            boolean enqueued = queueService.enqueue(queueName, "message-" + i);
            assertTrue(enqueued);
        }
        
        // Try to enqueue beyond capacity
        boolean enqueued = queueService.enqueue(queueName, "overflow-message");
        assertFalse(enqueued, "Should not enqueue beyond capacity");
        
        // Verify queue size
        assertEquals(10, queueService.getQueueSize(queueName));
        assertEquals(0, queueService.getRemainingCapacity(queueName));
    }
    
    @Test
    void testDequeueTimeout() throws InterruptedException {
        String queueName = "empty-queue";
        
        // Try to dequeue from empty queue with short timeout
        String message = queueService.dequeue(queueName, 100, TimeUnit.MILLISECONDS);
        assertNull(message, "Should return null when queue is empty and timeout occurs");
    }
    
    @Test
    void testMultipleQueues() throws InterruptedException {
        String queue1 = "queue-1";
        String queue2 = "queue-2";
        
        // Enqueue to different queues
        queueService.enqueue(queue1, "message-1");
        queueService.enqueue(queue2, "message-2");
        
        // Verify each queue has its own messages
        assertEquals(1, queueService.getQueueSize(queue1));
        assertEquals(1, queueService.getQueueSize(queue2));
        
        // Dequeue from specific queues
        assertEquals("message-1", queueService.dequeue(queue1, 1, TimeUnit.SECONDS));
        assertEquals("message-2", queueService.dequeue(queue2, 1, TimeUnit.SECONDS));
    }
    
    @Test
    void testClearQueue() {
        String queueName = "test-queue";
        
        // Enqueue messages
        for (int i = 0; i < 5; i++) {
            queueService.enqueue(queueName, "message-" + i);
        }
        
        assertEquals(5, queueService.getQueueSize(queueName));
        
        // Clear queue
        queueService.clearQueue(queueName);
        
        assertEquals(0, queueService.getQueueSize(queueName));
    }
    
    @Test
    void testGetQueueNames() {
        // Create multiple queues
        queueService.enqueue("queue-1", "message");
        queueService.enqueue("queue-2", "message");
        queueService.enqueue("queue-3", "message");
        
        // Verify queue names
        var queueNames = queueService.getQueueNames();
        assertEquals(3, queueNames.size());
        assertTrue(queueNames.contains("queue-1"));
        assertTrue(queueNames.contains("queue-2"));
        assertTrue(queueNames.contains("queue-3"));
    }
    
    @Test
    void testShutdown() {
        String queueName = "test-queue";
        
        // Enqueue before shutdown
        assertTrue(queueService.enqueue(queueName, "message"));
        
        // Shutdown
        queueService.shutdown();
        
        // Verify shutdown state
        assertTrue(queueService.isShutdown());
        
        // Try to enqueue after shutdown
        assertFalse(queueService.enqueue(queueName, "message-after-shutdown"));
    }
}
