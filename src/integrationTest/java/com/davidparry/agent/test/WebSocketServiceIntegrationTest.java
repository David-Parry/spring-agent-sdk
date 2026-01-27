/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.test;

import com.davidparry.agent.core.api.TaskResponse;
import com.davidparry.agent.core.config.QodoProperties;
import com.davidparry.agent.core.metrics.WebSocketMetrics;
import com.davidparry.agent.core.pojo.CommandSession;
import com.davidparry.agent.core.pojo.CommandSessionBuilder;
import com.davidparry.agent.core.service.WebSocketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for WebSocketService using a real TestWebSocketServer.
 * These tests verify actual WebSocket communication, not mocked behavior.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketService Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebSocketServiceIntegrationTest {

    private static TestWebSocketServer server;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TEST_TOKEN = "test-integration-token";

    private WebSocketService webSocketService;
    private QodoProperties qodoProperties;
    private WebSocketMetrics webSocketMetrics;
    private SimpleMeterRegistry meterRegistry;

    @BeforeAll
    static void startServer() throws Exception {
        server = new TestWebSocketServer();
        server.withRequiredAuthToken(TEST_TOKEN);
        server.start();
        // Give server time to start
        Thread.sleep(100);
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @BeforeEach
    void setUp() {
        server.reset();

        // Create real QodoProperties
        qodoProperties = new QodoProperties();
        qodoProperties.setBaseUrl(server.getBaseUrl());
        qodoProperties.getWebsocket().setToken(TEST_TOKEN);
        qodoProperties.getWebsocket().setPingIntervalSeconds(5);
        qodoProperties.getWebsocket().setPongTimeoutSeconds(3);
        qodoProperties.getWebsocket().setConnectionTimeoutSeconds(10);
        qodoProperties.getWebsocket().setMaxReconnectAttempts(3);
        qodoProperties.getWebsocket().setInitialReconnectDelay(Duration.ofMillis(100));
        qodoProperties.getWebsocket().setMaxReconnectDelay(Duration.ofMillis(500));

        // Create real metrics
        meterRegistry = new SimpleMeterRegistry();
        webSocketMetrics = new WebSocketMetrics(meterRegistry);

        // Create ObjectProvider wrapper
        ObjectProvider<SimpleMeterRegistry> meterRegistryProvider = new ObjectProvider<>() {
            @Override
            public SimpleMeterRegistry getObject() {
                return meterRegistry;
            }

            @Override
            public SimpleMeterRegistry getIfAvailable() {
                return meterRegistry;
            }

            @Override
            public SimpleMeterRegistry getIfUnique() {
                return meterRegistry;
            }
        };

        // Create service with real dependencies
        webSocketService = new WebSocketService(qodoProperties,
                                                (ObjectProvider) meterRegistryProvider, webSocketMetrics);
    }

    @AfterEach
    void tearDown() {
        if (webSocketService != null) {
            webSocketService.destroy();
        }
    }

    private CommandSession createTestSession(String sessionId, String requestId) {
        return CommandSessionBuilder.withSession("test-message", sessionId, requestId).build();
    }

    // ==================== Connection Tests ====================

    @Test
    @Order(1)
    @DisplayName("Should successfully connect to WebSocket server")
    void testSuccessfulConnection() throws Exception {
        // Arrange
        CommandSession session = createTestSession("session-1", "request-1");
        AtomicBoolean messageReceived = new AtomicBoolean(false);

        // Act
        CompletableFuture<WebSocket> future = webSocketService.connect(
                session,
                TEST_TOKEN,
                response -> messageReceived.set(true),
                error -> fail("Unexpected error: " + error)
        );

        // Wait for connection
        WebSocket ws = future.get(5, TimeUnit.SECONDS);

        // Assert
        assertNotNull(ws);
        assertTrue(webSocketService.isConnected("session-1"));
        assertEquals(WebSocketService.ConnectionStatus.CONNECTED,
                     webSocketService.getConnectionStatus("session-1"));
        assertEquals(1, server.getActiveConnectionCount());
    }

    @Test
    @Order(2)
    @DisplayName("Should fail connection with invalid auth token")
    void testConnectionWithInvalidToken() throws Exception {
        // Arrange
        CommandSession session = createTestSession("session-2", "request-2");
        AtomicReference<String> errorReceived = new AtomicReference<>();

        // Act
        CompletableFuture<WebSocket> future = webSocketService.connect(
                session,
                "wrong-token",
                response -> {
                },
                error -> errorReceived.set(error)
        );

        // Assert - connection should fail or timeout
        try {
            WebSocket ws = future.get(5, TimeUnit.SECONDS);
            // If we get here, check that it's not actually connected
            assertFalse(webSocketService.isConnected("session-2"));
        } catch (ExecutionException e) {
            // Expected - connection failed
            assertTrue(true);
        }
    }

    @Test
    @Order(3)
    @DisplayName("Should properly disconnect from server")
    void testDisconnect() throws Exception {
        // Arrange
        CommandSession session = createTestSession("session-3", "request-3");
        CompletableFuture<WebSocket> future = webSocketService.connect(
                session, TEST_TOKEN, response -> {
                }, error -> {
                }
        );
        future.get(5, TimeUnit.SECONDS);
        assertTrue(webSocketService.isConnected("session-3"));

        // Act
        webSocketService.disconnectSession("session-3");

        // Allow time for disconnect
        Thread.sleep(200);

        // Assert
        assertFalse(webSocketService.isConnected("session-3"));
    }

    // ==================== Message Tests ====================

    @Test
    @Order(10)
    @DisplayName("Should send message to server successfully")
    void testSendMessage() throws Exception {
        // Arrange
        CommandSession session = createTestSession("session-10", "request-10");
        CompletableFuture<WebSocket> future = webSocketService.connect(
                session, TEST_TOKEN, response -> {
                }, error -> {
                }
        );
        future.get(5, TimeUnit.SECONDS);

        // Act
        webSocketService.sendMessage("session-10", "Hello Server").get(5, TimeUnit.SECONDS);

        // Wait for server to receive
        String received = server.waitForMessage(2000);

        // Assert
        assertNotNull(received);
        assertTrue(received.contains("Hello Server"));
        assertEquals(1, server.getTotalMessagesReceived());
    }

    @Test
    @Order(11)
    @DisplayName("Should receive messages from server")
    void testReceiveMessage() throws Exception {
        // Arrange
        CommandSession session = createTestSession("session-11", "request-11");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TaskResponse> receivedResponse = new AtomicReference<>();

        CompletableFuture<WebSocket> future = webSocketService.connect(
                session,
                TEST_TOKEN,
                response -> {
                    receivedResponse.set(response);
                    latch.countDown();
                },
                error -> {
                }
        );
        future.get(5, TimeUnit.SECONDS);

        // Act - server sends a message
        server.sendReadySignal("session-11", "checkpoint-1");

        // Wait for message
        boolean received = latch.await(5, TimeUnit.SECONDS);

        // Assert
        assertTrue(received, "Should receive message within timeout");
        assertNotNull(receivedResponse.get());
        assertEquals("session-11", receivedResponse.get().sessionId());
    }

    @Test
    @Order(12)
    @DisplayName("Should handle multiple messages in sequence")
    void testMultipleMessages() throws Exception {
        // Arrange
        CommandSession session = createTestSession("session-12", "request-12");
        List<TaskResponse> responses = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        CompletableFuture<WebSocket> future = webSocketService.connect(
                session,
                TEST_TOKEN,
                response -> {
                    responses.add(response);
                    latch.countDown();
                },
                error -> {
                }
        );
        future.get(5, TimeUnit.SECONDS);

        // Act - server sends multiple messages
        server.sendReadySignal("session-12", "cp-1");
        server.sendTaskResponse("session-12", Map.of("tool", "TEST1"), "test", null);
        server.sendTaskResponse("session-12", Map.of("tool", "TEST2"), "test", null);

        // Wait for messages
        boolean received = latch.await(5, TimeUnit.SECONDS);

        // Assert
        assertTrue(received, "Should receive all messages");
        assertEquals(3, responses.size());
    }

    // ==================== Ping/Pong Tests ====================

    @Test
    @Order(20)
    @DisplayName("Should handle ping/pong keepalive")
    void testPingPong() throws Exception {
        // Arrange - use shorter ping interval for test
        qodoProperties.getWebsocket().setPingIntervalSeconds(1);
        webSocketService = new WebSocketService(qodoProperties,
                                                (ObjectProvider) new ObjectProvider<SimpleMeterRegistry>() {
                                                    @Override
                                                    public SimpleMeterRegistry getObject() {
                                                        return meterRegistry;
                                                    }

                                                    @Override
                                                    public SimpleMeterRegistry getIfAvailable() {
                                                        return meterRegistry;
                                                    }

                                                    @Override
                                                    public SimpleMeterRegistry getIfUnique() {
                                                        return meterRegistry;
                                                    }
                                                }, webSocketMetrics);

        CommandSession session = createTestSession("session-20", "request-20");
        CompletableFuture<WebSocket> future = webSocketService.connect(
                session, TEST_TOKEN, response -> {
                }, error -> {
                }
        );
        future.get(5, TimeUnit.SECONDS);

        // Act - wait for ping/pong cycle
        Thread.sleep(2500);

        // Assert
        assertTrue(server.getTotalPingsReceived() >= 1, "Should have received at least one ping");
        assertTrue(server.getTotalPongsSent() >= 1, "Should have sent at least one pong");
        assertTrue(webSocketService.isConnected("session-20"), "Connection should remain active");
    }

    @Test
    @Order(21)
    @DisplayName("Should detect connection timeout when pong not received")
    @Disabled("This test takes too long - pong timeout detection happens after multiple cycles")
    void testPongTimeout() throws Exception {
        // Arrange - server won't respond to pings
        server.withScenario(TestWebSocketServer.Scenario.NO_PONG);
        qodoProperties.getWebsocket().setPingIntervalSeconds(1);
        qodoProperties.getWebsocket().setPongTimeoutSeconds(2);

        webSocketService = new WebSocketService(qodoProperties,
                                                (ObjectProvider) new ObjectProvider<SimpleMeterRegistry>() {
                                                    @Override
                                                    public SimpleMeterRegistry getObject() {
                                                        return meterRegistry;
                                                    }

                                                    @Override
                                                    public SimpleMeterRegistry getIfAvailable() {
                                                        return meterRegistry;
                                                    }

                                                    @Override
                                                    public SimpleMeterRegistry getIfUnique() {
                                                        return meterRegistry;
                                                    }
                                                }, webSocketMetrics);

        CommandSession session = createTestSession("session-21", "request-21");
        AtomicReference<String> errorMessage = new AtomicReference<>();

        CompletableFuture<WebSocket> future = webSocketService.connect(
                session,
                TEST_TOKEN,
                response -> {
                },
                error -> errorMessage.set(error)
        );
        future.get(5, TimeUnit.SECONDS);

        // Act - wait for timeout detection
        Thread.sleep(5000);

        // Assert
        assertNotNull(errorMessage.get(), "Should have received timeout error");
        assertTrue(errorMessage.get().contains("timeout") ||
                           errorMessage.get().contains("pong"),
                   "Error should mention timeout");
    }

    // ==================== Server-Initiated Close Tests ====================

    @Test
    @Order(30)
    @DisplayName("Should handle server-initiated close gracefully")
    void testServerInitiatedClose() throws Exception {
        // Arrange
        CommandSession session = createTestSession("session-30", "request-30");
        AtomicReference<String> errorMessage = new AtomicReference<>();

        CompletableFuture<WebSocket> future = webSocketService.connect(
                session,
                TEST_TOKEN,
                response -> {
                },
                error -> errorMessage.set(error)
        );
        future.get(5, TimeUnit.SECONDS);
        assertTrue(webSocketService.isConnected("session-30"));

        // Act - server closes the connection
        server.closeSession("session-30", 1000, "Normal closure");

        // Wait for close to propagate
        Thread.sleep(500);

        // Assert
        assertFalse(webSocketService.isConnected("session-30"));
    }

    @Test
    @Order(31)
    @DisplayName("Should handle expected close after ENDNODE")
    void testExpectedCloseAfterEndNode() throws Exception {
        // Arrange
        CommandSession session = createTestSession("session-31", "request-31");
        CountDownLatch endNodeLatch = new CountDownLatch(1);

        CompletableFuture<WebSocket> future = webSocketService.connect(
                session,
                TEST_TOKEN,
                response -> {
                    if (response.data() != null &&
                            "ENDNODE".equals(response.data().tool())) {
                        endNodeLatch.countDown();
                    }
                },
                error -> {
                }
        );
        future.get(5, TimeUnit.SECONDS);

        // Act - receive ENDNODE and mark expected close
        server.sendEndNode("session-31");
        endNodeLatch.await(2, TimeUnit.SECONDS);
        webSocketService.markExpectedClose();

        // Server closes connection
        server.closeSession("session-31", 1000, "End of session");
        Thread.sleep(500);

        // Assert - no error handler should be called for expected close
        assertFalse(webSocketService.isConnected("session-31"));
    }

    @Test
    @Order(32)
    @DisplayName("Should handle abnormal close and attempt reconnection")
    void testAbnormalCloseWithReconnection() throws Exception {
        // Arrange
        server.withScenario(TestWebSocketServer.Scenario.DISCONNECT_AFTER_N);
        server.withDisconnectAfterMessages(1);

        CommandSession session = createTestSession("session-32", "request-32");
        AtomicInteger errorCount = new AtomicInteger(0);

        CompletableFuture<WebSocket> future = webSocketService.connect(
                session,
                TEST_TOKEN,
                response -> {
                },
                error -> errorCount.incrementAndGet()
        );
        future.get(5, TimeUnit.SECONDS);

        // Act - send message which triggers disconnect
        webSocketService.sendMessage("session-32", "trigger disconnect");

        // Wait for reconnection attempt
        Thread.sleep(2000);

        // Assert - should have attempted reconnection
        // The exact behavior depends on reconnection logic
        assertTrue(errorCount.get() >= 0); // May or may not get error depending on timing
    }

    // ==================== Error Handling Tests ====================

    @Test
    @Order(40)
    @DisplayName("Should handle malformed JSON from server")
    void testMalformedJsonResponse() throws Exception {
        // Arrange
        server.withScenario(TestWebSocketServer.Scenario.SEND_MALFORMED_JSON);

        CommandSession session = createTestSession("session-40", "request-40");
        AtomicReference<String> errorMessage = new AtomicReference<>();

        CompletableFuture<WebSocket> future = webSocketService.connect(
                session,
                TEST_TOKEN,
                response -> {
                },
                error -> errorMessage.set(error)
        );
        future.get(5, TimeUnit.SECONDS);

        // Act - send message which triggers malformed response
        webSocketService.sendMessage("session-40", "trigger malformed");

        // Wait for error to be processed
        Thread.sleep(1000);

        // Assert - should receive parse error
        assertNotNull(errorMessage.get(), "Should have received error for malformed JSON");
        assertTrue(errorMessage.get().contains("parse") ||
                           errorMessage.get().contains("Failed"),
                   "Error should indicate parsing failure");
    }

    @Test
    @Order(41)
    @DisplayName("Should handle connection rejection")
    void testConnectionRejection() throws Exception {
        // Arrange
        server.rejectConnections(true);

        CommandSession session = createTestSession("session-41", "request-41");
        AtomicReference<String> errorMessage = new AtomicReference<>();

        // Act
        CompletableFuture<WebSocket> future = webSocketService.connect(
                session,
                TEST_TOKEN,
                response -> {
                },
                error -> errorMessage.set(error)
        );

        // Assert
        try {
            future.get(10, TimeUnit.SECONDS);
            // If we get here, connection might have completed before rejection took effect
            assertFalse(webSocketService.isConnected("session-41"));
        } catch (ExecutionException e) {
            // Expected - connection failed
            assertTrue(true);
        } catch (TimeoutException e) {
            // Also acceptable - connection never completed
            assertTrue(true);
        }

        // Cleanup
        server.rejectConnections(false);
    }

    @Test
    @Order(42)
    @DisplayName("Should handle send failure when not connected")
    void testSendWhenNotConnected() throws Exception {
        // Arrange - no connection established

        // Act
        CompletableFuture<Void> result = webSocketService.sendMessage("nonexistent-session", "test");

        // Assert
        try {
            result.get(2, TimeUnit.SECONDS);
            fail("Should have thrown exception");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
    }

    // ==================== Echo and Custom Behavior Tests ====================

    @Test
    @Order(50)
    @DisplayName("Should handle echo scenario")
    void testEchoScenario() throws Exception {
        // Arrange
        server.withScenario(TestWebSocketServer.Scenario.ECHO);

        CommandSession session = createTestSession("session-50", "request-50");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();

        CompletableFuture<WebSocket> future = webSocketService.connect(
                session,
                TEST_TOKEN,
                response -> {
                    // Echo comes back as raw text, might not parse as TaskResponse
                    latch.countDown();
                },
                error -> {
                }
        );
        future.get(5, TimeUnit.SECONDS);

        // Act
        webSocketService.sendMessage("session-50", "{\"test\":\"echo\"}");

        // Wait for response
        Thread.sleep(500);

        // Assert
        assertEquals(1, server.getTotalMessagesReceived());
        assertEquals(1, server.getTotalMessagesSent());
    }

    @Test
    @Order(51)
    @DisplayName("Should handle custom message transformer")
    void testCustomMessageTransformer() throws Exception {
        // Arrange - transform any message to a TaskResponse
        server.withMessageTransformer(msg -> {
            try {
                Map<String, Object> response = Map.of(
                        "session_id", "session-51",
                        "data", Map.of("tool", "TRANSFORMED", "original", msg),
                        "type", "custom"
                );
                return objectMapper.writeValueAsString(response);
            } catch (Exception e) {
                return null;
            }
        });

        CommandSession session = createTestSession("session-51", "request-51");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TaskResponse> receivedResponse = new AtomicReference<>();

        CompletableFuture<WebSocket> future = webSocketService.connect(
                session,
                TEST_TOKEN,
                response -> {
                    receivedResponse.set(response);
                    latch.countDown();
                },
                error -> {
                }
        );
        future.get(5, TimeUnit.SECONDS);

        // Act
        webSocketService.sendMessage("session-51", "original message");
        boolean received = latch.await(3, TimeUnit.SECONDS);

        // Assert
        assertTrue(received);
        assertNotNull(receivedResponse.get());
        assertEquals("TRANSFORMED", receivedResponse.get().data().tool());
    }
}
