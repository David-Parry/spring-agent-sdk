/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.service;

import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQSslConnectionFactory;

import java.util.Map;

public class TopicSubscriber {
    public static void main(String[] args) throws Exception {

        // Check for prefix in args[0], default to "LOCAL" if not provided
        String prefix = "LOCAL";
        if (args.length > 0 && args[0] != null && !args[0].trim().isEmpty()) {
            prefix = args[0];
        }

        EnvFileLoader envFileLoader = new EnvFileLoader();
        Map<String, String> envProperties = envFileLoader.loadEnvFile(envFileLoader.findEnvFile());

        String brokerUrl = envProperties.get(prefix + "_SPRING_ACTIVEMQ_BROKER_URL");
        String username = envProperties.get(prefix + "_ACTIVEMQ_USERNAME");
        String password = envProperties.get(prefix + "_ACTIVEMQ_PASSWORD");

        // Determine if SSL is needed based on broker URL
        ConnectionFactory factory;
        if (brokerUrl != null && brokerUrl.startsWith("ssl://")) {
            ActiveMQSslConnectionFactory sslFactory = new ActiveMQSslConnectionFactory(brokerUrl);
            sslFactory.setUserName(username);
            sslFactory.setPassword(password);
            factory = sslFactory;
        } else {
            ActiveMQConnectionFactory tcpFactory = new ActiveMQConnectionFactory(brokerUrl);
            tcpFactory.setUserName(username);
            tcpFactory.setPassword(password);
            factory = tcpFactory;
        }

        startListener(factory);

    }

    public static void startListener(ConnectionFactory factory) throws Exception {

        Connection connection = factory.createConnection();
        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // Subscribe to all three topics
        Topic auditTopic = session.createTopic("audit");
        Topic eventTopic = session.createTopic("event");
        Topic responseTopic = session.createTopic("response");

        MessageConsumer auditConsumer = session.createConsumer(auditTopic);
        MessageConsumer eventConsumer = session.createConsumer(eventTopic);
        MessageConsumer responseConsumer = session.createConsumer(responseTopic);

        MessageListener listener = message -> {
            try {
                if (message instanceof TextMessage textMessage) {
                    System.out.println("===================================");
                    System.out.println("Destination: " + message.getJMSDestination());
                    System.out.println("Content: " + textMessage.getText());
                    System.out.println("===================================");
                }
            } catch (JMSException e) {
                e.printStackTrace();
            }
        };

        auditConsumer.setMessageListener(listener);
        eventConsumer.setMessageListener(listener);
        responseConsumer.setMessageListener(listener);

        System.out.println("Listening for messages... Press Ctrl+C to exit");
        Thread.sleep(Long.MAX_VALUE);
    }

}
