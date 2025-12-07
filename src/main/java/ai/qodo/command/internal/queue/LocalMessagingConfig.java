/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * Configuration class for local messaging infrastructure.
 * Provides beans for the local queue implementation when messaging.provider is set to "local".
 */
@Configuration
@ConditionalOnProperty(name = "messaging.provider", havingValue = "local")
public class LocalMessagingConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalMessagingConfig.class);
    
    public LocalMessagingConfig() {
        logger.info("LocalMessagingConfig initialized - using local in-memory queues");
    }
    
    /**
     * Simple transaction manager for local queue operations.
     * Provides basic transaction semantics without full ACID guarantees.
     * 
     * Note: This is a simplified implementation. For production use with
     * strict transactional requirements, consider using a more robust solution.
     */
    @Bean
    @ConditionalOnProperty(name = "messaging.provider", havingValue = "local")
    public PlatformTransactionManager localTransactionManager() {
        logger.info("Creating LocalTransactionManager for local queue operations");
        
        return new AbstractPlatformTransactionManager() {
            
            @Override
            protected Object doGetTransaction() {
                // Return a simple transaction object
                return new LocalTransaction();
            }
            
            @Override
            protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
                LocalTransaction tx = (LocalTransaction) transaction;
                tx.begin();
                logger.debug("Local transaction begun");
            }
            
            @Override
            protected void doCommit(DefaultTransactionStatus status) {
                LocalTransaction tx = (LocalTransaction) status.getTransaction();
                tx.commit();
                logger.debug("Local transaction committed");
            }
            
            @Override
            protected void doRollback(DefaultTransactionStatus status) {
                LocalTransaction tx = (LocalTransaction) status.getTransaction();
                tx.rollback();
                logger.debug("Local transaction rolled back");
            }
        };
    }
    
    /**
     * Simple transaction object for local queue operations.
     */
    private static class LocalTransaction {
        private boolean active = false;
        
        public void begin() {
            this.active = true;
        }
        
        public void commit() {
            this.active = false;
        }
        
        public void rollback() {
            this.active = false;
        }
        
        public boolean isActive() {
            return active;
        }
    }
}
