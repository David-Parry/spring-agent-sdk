/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.queue;

import ai.qodo.command.internal.config.MessagingProperties;
import ai.qodo.command.internal.service.MessageRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local message consumer that listens to messages from local queues
 * and routes them to appropriate services using the MessageRouter.
 * 
 * This implementation is activated when messaging.provider is set to "local".
 * Uses virtual threads for efficient concurrent message processing while
 * maintaining thread separation from publishers.
 */
@Service
@ConditionalOnProperty(name = "messaging.provider", havingValue = "local")
public class LocalMessageConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalMessageConsumer.class);
    
    private final LocalQueueService queueService;
    private final MessageRouter messageRouter;
    private final MessagingProperties messagingProperties;
    private final LocalQueueProperties localQueueProperties;
    private final LocalRetryHandler retryHandler;
    
    private ExecutorService consumerExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<String> activeQueues = new ArrayList<>();
    
    public LocalMessageConsumer(
            LocalQueueService queueService,
            MessageRouter messageRouter,
            MessagingProperties messagingProperties,
            LocalQueueProperties localQueueProperties,
            LocalRetryHandler retryHandler) {
        this.queueService = queueService;
        this.messageRouter = messageRouter;
        this.messagingProperties = messagingProperties;
        this.localQueueProperties = localQueueProperties;
        this.retryHandler = retryHandler;
    }
    
    @PostConstruct
    public void startConsumers() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting LocalMessageConsumer with {} consumer threads per queue", 
                       localQueueProperties.getConsumerThreads());
            
            // Use virtual threads for efficient concurrent processing
            consumerExecutor = Executors.newVirtualThreadPerTaskExecutor();
            
            // Start consumers for event queue
            String eventQueue = messagingProperties.getQueue().getEvent();
            startConsumerForQueue(eventQueue);
            activeQueues.add(eventQueue);
            
            // Start consumers for response queue
            String responseQueue = messagingProperties.getQueue().getResponse();
            startConsumerForQueue(responseQueue);
            activeQueues.add(responseQueue);
            
            logger.info("LocalMessageConsumer started successfully for queues: {}", activeQueues);
        }
    }
    
    /**
     * Starts consumer threads for a specific queue.
     * 
     * @param queueName The name of the queue to consume from
     */
    private void startConsumerForQueue(String queueName) {
        int threadCount = localQueueProperties.getConsumerThreads();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            consumerExecutor.submit(() -> consumeMessages(queueName, threadIndex));
            logger.info("Started consumer thread {} for queue '{}'", threadIndex, queueName);
        }
    }
    
    /**
     * Main consumer loop that continuously polls for messages and processes them.
     * 
     * @param queueName The name of the queue to consume from
     * @param threadIndex The index of this consumer thread
     */
    private void consumeMessages(String queueName, int threadIndex) {
        logger.info("Consumer thread {} for queue '{}' is now running", threadIndex, queueName);
        
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // Poll for message with timeout
                String message = queueService.dequeue(
                    queueName, 
                    localQueueProperties.getPollTimeoutSeconds(), 
                    TimeUnit.SECONDS
                );
                
                if (message != null) {
                    processMessage(queueName, message);
                }
                
            } catch (InterruptedException e) {
                logger.info("Consumer thread {} for queue '{}' interrupted", threadIndex, queueName);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Unexpected error in consumer thread {} for queue '{}'", 
                           threadIndex, queueName, e);
                // Continue processing despite errors
            }
        }
        
        logger.info("Consumer thread {} for queue '{}' stopped", threadIndex, queueName);
    }
    
    /**
     * Processes a single message with retry logic.
     * 
     * @param queueName The queue the message came from
     * @param message The message to process
     */
    private void processMessage(String queueName, String message) {
        logger.info("Received message from local queue '{}': {}", 
                   queueName,
                   message.length() > 200 ? message.substring(0, 200) + "..." : message);
        logger.debug("Full message content: {}", message);
        
        try {
            // Process the message through the router
            messageRouter.processMessage(message);
            logger.debug("Successfully processed message from queue '{}'", queueName);
            
        } catch (Exception e) {
            logger.error("Error processing message from queue '{}': {}", queueName, e.getMessage(), e);
            
            // Handle retry logic
            boolean requeued = retryHandler.handleFailedMessage(queueName, message, e);
            
            if (!requeued) {
                logger.error("Message processing failed and could not be requeued. Message may be lost: {}", 
                           message.length() > 100 ? message.substring(0, 100) + "..." : message);
            }
        }
    }
    
    @PreDestroy
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            logger.info("Shutting down LocalMessageConsumer...");
            
            if (consumerExecutor != null) {
                consumerExecutor.shutdown();
                
                try {
                    if (!consumerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                        logger.warn("Consumer threads did not terminate within timeout, forcing shutdown");
                        consumerExecutor.shutdownNow();
                        
                        if (!consumerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                            logger.error("Consumer threads did not terminate after forced shutdown");
                        }
                    }
                } catch (InterruptedException e) {
                    logger.error("Interrupted while waiting for consumer threads to terminate", e);
                    consumerExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            
            logger.info("LocalMessageConsumer shutdown complete");
        }
    }
    
    /**
     * Checks if the consumer is currently running.
     * 
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Gets the list of active queue names being consumed.
     * 
     * @return List of queue names
     */
    public List<String> getActiveQueues() {
        return new ArrayList<>(activeQueues);
    }
}
