/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.service;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;

import static ai.qodo.command.handlers.JiraAgentHandler.JIRA_BUG_ACTIONABLE;

/**
 * Test class for publishing messages to ActiveMQ running in local docker-compose.
 * 
 * Prerequisites:
 * 1. Start docker-compose: docker-compose up -d
 * 2. Ensure ActiveMQ is running on localhost:61616
 * 
 * Usage:
 * - Run this test to publish a test message to the response topic
 * - Check ActiveMQ web console at http://localhost:8161 (admin/admin)
 * - Verify the message was published to the 'response' topic
 */
public class ActiveMQPublisherTest {
    
    private static final Logger logger = LoggerFactory.getLogger(ActiveMQPublisherTest.class);
    
    // ActiveMQ connection details from docker-compose.yml and .env
    private static final String BROKER_URL = "tcp://localhost:61616";
    private static final String USERNAME = "qodo";
    private static final String PASSWORD = "qodo";
    private static final String RESPONSE_TOPIC = "response";
    
    private Connection connection;
    private Session session;
    private MessageProducer producer;
    
    @BeforeEach
    public void setUp() throws JMSException {
        try {
            logger.info("Setting up ActiveMQ connection to {}", BROKER_URL);

            // Create connection factory
            ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(USERNAME, PASSWORD, BROKER_URL);

            // Create connection
            connection = connectionFactory.createConnection();
            connection.start();

            // Create session
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Create topic
            Topic topic = session.createTopic(RESPONSE_TOPIC);

            // Create producer
            producer = session.createProducer(topic);

            logger.info("ActiveMQ connection established successfully");
        } catch (Exception e) {
            logger.error("Unable to setup ",e);
        }
    }
    
    @AfterEach
    public void tearDown() {
        logger.info("Closing ActiveMQ connection");
        
        try {
            if (producer != null) {
                producer.close();
            }
            if (session != null) {
                session.close();
            }
            if (connection != null) {
                connection.close();
            }
            logger.info("ActiveMQ connection closed successfully");
        } catch (JMSException e) {
            logger.error("Error closing ActiveMQ connection", e);
        }
    }
    

    
    /**
     * Example test showing how to publish a JSON message.
     */
    @Test
    public void testPublishJsonMessage() throws JMSException {
        try {
            String jsonMessage = """
                    {   "type": "%s",
                        "timestamp": %d,
                        "issueKey": "SCRUM-151",
                        "summary": "Root Cause: Missing client-side date validation allowing invalid dates (like '1990-01-0') to be submitted to the backend."
                    }
                    """.formatted(JIRA_BUG_ACTIONABLE, System.currentTimeMillis());

            logger.info("Publishing JSON message to topic '{}': {}", RESPONSE_TOPIC, jsonMessage);

            TextMessage message = session.createTextMessage(jsonMessage);
            producer.send(message);

            logger.info("JSON message published successfully to topic '{}'", RESPONSE_TOPIC);
        } catch (Exception e) {
            logger.error("Error publishing JSON message", e);
        }
    }

}
