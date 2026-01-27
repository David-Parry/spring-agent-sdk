/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A lightweight test WebSocket server for integration testing of WebSocketService.
 * Implements RFC 6455 WebSocket protocol for testing various scenarios.
 */
public class TestWebSocketServer implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(TestWebSocketServer.class);
    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final int port;
    private final HttpServer httpServer;
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, WebSocketConnection> connections = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger connectionCounter = new AtomicInteger(0);

    // Configurable behaviors for different test scenarios
    private volatile Scenario scenario = Scenario.NORMAL;
    private volatile int disconnectAfterMessages = -1;
    private volatile long messageDelayMs = 0;
    private volatile boolean rejectConnections = false;
    private volatile String authToken = null;
    private volatile Consumer<String> messageReceivedCallback;
    private volatile Function<String, String> messageTransformer;
    private final BlockingQueue<String> receivedMessages = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> sentMessages = new LinkedBlockingQueue<>();
    
    // Counters for verification
    private final AtomicInteger totalMessagesReceived = new AtomicInteger(0);
    private final AtomicInteger totalMessagesSent = new AtomicInteger(0);
    private final AtomicInteger totalPingsReceived = new AtomicInteger(0);
    private final AtomicInteger totalPongsSent = new AtomicInteger(0);

    public enum Scenario {
        NORMAL,                    // Normal operation
        SLOW_RESPONSE,             // Delayed responses
        NO_PONG,                   // Don't respond to pings
        DISCONNECT_ON_MESSAGE,     // Disconnect after receiving a message
        DISCONNECT_AFTER_N,        // Disconnect after N messages
        SEND_MALFORMED_JSON,       // Send invalid JSON responses
        CLOSE_IMMEDIATELY,         // Close connection immediately after handshake
        REJECT_AUTH,               // Reject authentication
        ECHO,                      // Echo messages back
        CUSTOM                     // Custom behavior via transformer
    }

    /**
     * Creates a new TestWebSocketServer on an available port.
     */
    public TestWebSocketServer() throws IOException {
        this(0); // 0 = auto-assign available port
    }

    /**
     * Creates a new TestWebSocketServer on the specified port.
     */
    public TestWebSocketServer(int port) throws IOException {
        this.port = port;
        this.httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "test-ws-server-" + connectionCounter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        httpServer.setExecutor(executor);
        httpServer.createContext("/v2/agentic/ws/connect", new WebSocketUpgradeHandler());
        httpServer.createContext("/ws", new WebSocketUpgradeHandler());
        httpServer.createContext("/health", exchange -> {
            String response = "OK";
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });
    }

    /**
     * Starts the server.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            httpServer.start();
            logger.info("TestWebSocketServer started on port {}", getPort());
        }
    }

    /**
     * Gets the actual port the server is running on.
     */
    public int getPort() {
        return httpServer.getAddress().getPort();
    }

    /**
     * Gets the WebSocket URL for clients to connect to.
     */
    public String getWebSocketUrl() {
        return String.format("ws://localhost:%d/v2/agentic/ws/connect", getPort());
    }

    /**
     * Gets the base URL (http) for constructing WebSocket URLs.
     */
    public String getBaseUrl() {
        return String.format("http://localhost:%d", getPort());
    }

    // ==================== Configuration Methods ====================

    public TestWebSocketServer withScenario(Scenario scenario) {
        this.scenario = scenario;
        return this;
    }

    public TestWebSocketServer withDisconnectAfterMessages(int count) {
        this.disconnectAfterMessages = count;
        this.scenario = Scenario.DISCONNECT_AFTER_N;
        return this;
    }

    public TestWebSocketServer withMessageDelay(long delayMs) {
        this.messageDelayMs = delayMs;
        return this;
    }

    public TestWebSocketServer withRequiredAuthToken(String token) {
        this.authToken = token;
        return this;
    }

    public TestWebSocketServer withMessageReceivedCallback(Consumer<String> callback) {
        this.messageReceivedCallback = callback;
        return this;
    }

    public TestWebSocketServer withMessageTransformer(Function<String, String> transformer) {
        this.messageTransformer = transformer;
        this.scenario = Scenario.CUSTOM;
        return this;
    }

    public TestWebSocketServer rejectConnections(boolean reject) {
        this.rejectConnections = reject;
        return this;
    }

    // ==================== Server Actions ====================

    /**
     * Sends a message to all connected clients.
     */
    public void broadcast(String message) {
        connections.values().forEach(conn -> sendToConnection(conn, message));
    }

    /**
     * Sends a message to a specific session.
     */
    public void sendToSession(String sessionId, String message) {
        connections.values().stream()
                .filter(c -> sessionId.equals(c.sessionId))
                .findFirst()
                .ifPresent(conn -> sendToConnection(conn, message));
    }

    /**
     * Sends a TaskResponse JSON to all connected clients.
     */
    public void sendTaskResponse(String sessionId, Object data, String type, String subType) {
        try {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("session_id", sessionId);
            response.put("data", data);
            if (type != null) response.put("type", type);
            if (subType != null) response.put("sub_type", subType);
            String json = objectMapper.writeValueAsString(response);
            broadcast(json);
        } catch (Exception e) {
            logger.error("Failed to send TaskResponse", e);
        }
    }

    /**
     * Sends a READY signal to all connected clients.
     */
    public void sendReadySignal(String sessionId, String checkpointId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", "READY");
        if (checkpointId != null) {
            data.put("checkpoint_id", checkpointId);
        }
        sendTaskResponse(sessionId, data, "ready", null);
    }

    /**
     * Sends an ENDNODE signal to all connected clients.
     */
    public void sendEndNode(String sessionId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", "ENDNODE");
        sendTaskResponse(sessionId, data, "end", null);
    }

    /**
     * Closes a specific session's connection.
     */
    public void closeSession(String sessionId, int statusCode, String reason) {
        connections.values().stream()
                .filter(c -> sessionId.equals(c.sessionId))
                .findFirst()
                .ifPresent(conn -> closeConnection(conn, statusCode, reason));
    }

    /**
     * Closes all connections.
     */
    public void closeAllConnections() {
        connections.values().forEach(conn -> closeConnection(conn, 1000, "Server shutdown"));
    }

    // ==================== Verification Methods ====================

    public int getActiveConnectionCount() {
        return connections.size();
    }

    public boolean hasConnection(String sessionId) {
        return connections.values().stream().anyMatch(c -> sessionId.equals(c.sessionId));
    }

    public List<String> getReceivedMessages() {
        return new ArrayList<>(receivedMessages);
    }

    public String waitForMessage(long timeoutMs) throws InterruptedException {
        return receivedMessages.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public List<String> getSentMessages() {
        return new ArrayList<>(sentMessages);
    }

    public int getTotalMessagesReceived() {
        return totalMessagesReceived.get();
    }

    public int getTotalMessagesSent() {
        return totalMessagesSent.get();
    }

    public int getTotalPingsReceived() {
        return totalPingsReceived.get();
    }

    public int getTotalPongsSent() {
        return totalPongsSent.get();
    }

    public void clearCounters() {
        receivedMessages.clear();
        sentMessages.clear();
        totalMessagesReceived.set(0);
        totalMessagesSent.set(0);
        totalPingsReceived.set(0);
        totalPongsSent.set(0);
    }

    public void reset() {
        clearCounters();
        scenario = Scenario.NORMAL;
        disconnectAfterMessages = -1;
        messageDelayMs = 0;
        rejectConnections = false;
        messageTransformer = null;
        messageReceivedCallback = null;
    }

    // ==================== WebSocket Protocol Implementation ====================

    private class WebSocketUpgradeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (rejectConnections) {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
                return;
            }

            // Check authentication if required
            if (authToken != null) {
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader == null || !authHeader.equals("Bearer " + authToken)) {
                    logger.info("Rejecting connection - invalid auth token");
                    exchange.sendResponseHeaders(401, -1);
                    exchange.close();
                    return;
                }
            }

            // Parse query parameters
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQueryParams(query);
            String sessionId = params.get("session_id");
            String requestId = params.get("request_id");

            // Validate WebSocket upgrade request
            String upgrade = exchange.getRequestHeaders().getFirst("Upgrade");
            String wsKey = exchange.getRequestHeaders().getFirst("Sec-WebSocket-Key");

            if (!"websocket".equalsIgnoreCase(upgrade) || wsKey == null) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                return;
            }

            // Complete WebSocket handshake
            String acceptKey = generateAcceptKey(wsKey);
            exchange.getResponseHeaders().add("Upgrade", "websocket");
            exchange.getResponseHeaders().add("Connection", "Upgrade");
            exchange.getResponseHeaders().add("Sec-WebSocket-Accept", acceptKey);
            exchange.sendResponseHeaders(101, -1);

            logger.info("WebSocket connection established for session: {}", sessionId);

            // Get raw socket and handle WebSocket frames
            // Note: HttpExchange doesn't provide direct socket access, so we use a workaround
            handleWebSocketConnection(exchange, sessionId, requestId);
        }
    }

    private void handleWebSocketConnection(HttpExchange exchange, String sessionId, String requestId) {
        String connId = UUID.randomUUID().toString();
        
        try {
            // Get the underlying streams
            InputStream in = exchange.getRequestBody();
            OutputStream out = exchange.getResponseBody();
            
            WebSocketConnection conn = new WebSocketConnection(connId, sessionId, requestId, in, out);
            connections.put(connId, conn);

            // Handle CLOSE_IMMEDIATELY scenario
            if (scenario == Scenario.CLOSE_IMMEDIATELY) {
                logger.info("CLOSE_IMMEDIATELY scenario - closing connection");
                closeConnection(conn, 1000, "Test close immediately");
                return;
            }

            // Start message reading loop
            executor.submit(() -> readMessages(conn));

        } catch (Exception e) {
            logger.error("Error handling WebSocket connection", e);
            connections.remove(connId);
        }
    }

    private void readMessages(WebSocketConnection conn) {
        try {
            while (conn.running.get() && running.get()) {
                WebSocketFrame frame = readFrame(conn.inputStream);
                if (frame == null) {
                    break;
                }

                switch (frame.opcode) {
                    case 0x01: // Text frame
                        handleTextMessage(conn, frame.payload);
                        break;
                    case 0x08: // Close frame
                        logger.info("Received close frame from client");
                        sendCloseFrame(conn, 1000, "Normal closure");
                        conn.running.set(false);
                        break;
                    case 0x09: // Ping frame
                        handlePing(conn, frame.payload);
                        break;
                    case 0x0A: // Pong frame
                        logger.debug("Received pong frame");
                        break;
                }
            }
        } catch (IOException e) {
            if (conn.running.get()) {
                logger.debug("Connection closed: {}", e.getMessage());
            }
        } finally {
            connections.remove(conn.id);
            conn.running.set(false);
            logger.info("Connection {} closed", conn.sessionId);
        }
    }

    private void handleTextMessage(WebSocketConnection conn, byte[] payload) {
        String message = new String(payload, StandardCharsets.UTF_8);
        totalMessagesReceived.incrementAndGet();
        conn.messageCount.incrementAndGet();
        receivedMessages.offer(message);

        logger.debug("Received message from {}: {}", conn.sessionId, message);

        // Notify callback if set
        if (messageReceivedCallback != null) {
            try {
                messageReceivedCallback.accept(message);
            } catch (Exception e) {
                logger.warn("Message callback error", e);
            }
        }

        // Apply message delay if configured
        if (messageDelayMs > 0) {
            try {
                Thread.sleep(messageDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Handle scenarios
        switch (scenario) {
            case DISCONNECT_ON_MESSAGE:
                closeConnection(conn, 1001, "Test disconnect on message");
                break;
            case DISCONNECT_AFTER_N:
                if (disconnectAfterMessages > 0 && conn.messageCount.get() >= disconnectAfterMessages) {
                    closeConnection(conn, 1001, "Disconnect after " + disconnectAfterMessages + " messages");
                }
                break;
            case SEND_MALFORMED_JSON:
                sendToConnection(conn, "{ invalid json [}");
                break;
            case ECHO:
                sendToConnection(conn, message);
                break;
            case CUSTOM:
                if (messageTransformer != null) {
                    String response = messageTransformer.apply(message);
                    if (response != null) {
                        sendToConnection(conn, response);
                    }
                }
                break;
            default:
                // NORMAL, SLOW_RESPONSE, NO_PONG - no automatic response
                break;
        }
    }

    private void handlePing(WebSocketConnection conn, byte[] payload) {
        totalPingsReceived.incrementAndGet();
        logger.debug("Received ping from {}", conn.sessionId);

        if (scenario != Scenario.NO_PONG) {
            sendPong(conn, payload);
            totalPongsSent.incrementAndGet();
        } else {
            logger.debug("NO_PONG scenario - not responding to ping");
        }
    }

    private void sendPong(WebSocketConnection conn, byte[] payload) {
        try {
            sendFrame(conn, 0x0A, payload); // 0x0A = pong
        } catch (IOException e) {
            logger.warn("Failed to send pong", e);
        }
    }

    private void sendToConnection(WebSocketConnection conn, String message) {
        try {
            byte[] payload = message.getBytes(StandardCharsets.UTF_8);
            sendFrame(conn, 0x01, payload); // 0x01 = text frame
            totalMessagesSent.incrementAndGet();
            sentMessages.offer(message);
            logger.debug("Sent message to {}: {}", conn.sessionId, message);
        } catch (IOException e) {
            logger.warn("Failed to send message", e);
        }
    }

    private void closeConnection(WebSocketConnection conn, int statusCode, String reason) {
        try {
            sendCloseFrame(conn, statusCode, reason);
        } catch (Exception e) {
            logger.debug("Error sending close frame", e);
        } finally {
            conn.running.set(false);
            connections.remove(conn.id);
        }
    }

    // ==================== WebSocket Frame Encoding/Decoding ====================

    private WebSocketFrame readFrame(InputStream in) throws IOException {
        int firstByte = in.read();
        if (firstByte == -1) return null;

        boolean fin = (firstByte & 0x80) != 0;
        int opcode = firstByte & 0x0F;

        int secondByte = in.read();
        if (secondByte == -1) return null;

        boolean masked = (secondByte & 0x80) != 0;
        int payloadLength = secondByte & 0x7F;

        if (payloadLength == 126) {
            payloadLength = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
        } else if (payloadLength == 127) {
            // 8-byte length (we'll read but truncate to int for simplicity)
            long longLength = 0;
            for (int i = 0; i < 8; i++) {
                longLength = (longLength << 8) | (in.read() & 0xFF);
            }
            payloadLength = (int) longLength;
        }

        byte[] maskKey = null;
        if (masked) {
            maskKey = new byte[4];
            in.read(maskKey);
        }

        byte[] payload = new byte[payloadLength];
        int read = 0;
        while (read < payloadLength) {
            int r = in.read(payload, read, payloadLength - read);
            if (r == -1) break;
            read += r;
        }

        // Unmask if masked
        if (masked && maskKey != null) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= maskKey[i % 4];
            }
        }

        return new WebSocketFrame(fin, opcode, payload);
    }

    private void sendFrame(WebSocketConnection conn, int opcode, byte[] payload) throws IOException {
        synchronized (conn.outputLock) {
            OutputStream out = conn.outputStream;
            
            // First byte: FIN bit + opcode
            out.write(0x80 | opcode);

            // Second byte: no mask (server->client) + payload length
            int length = payload.length;
            if (length <= 125) {
                out.write(length);
            } else if (length <= 65535) {
                out.write(126);
                out.write((length >> 8) & 0xFF);
                out.write(length & 0xFF);
            } else {
                out.write(127);
                for (int i = 7; i >= 0; i--) {
                    out.write((int) ((length >> (8 * i)) & 0xFF));
                }
            }

            out.write(payload);
            out.flush();
        }
    }

    private void sendCloseFrame(WebSocketConnection conn, int statusCode, String reason) throws IOException {
        byte[] reasonBytes = reason != null ? reason.getBytes(StandardCharsets.UTF_8) : new byte[0];
        byte[] payload = new byte[2 + reasonBytes.length];
        payload[0] = (byte) ((statusCode >> 8) & 0xFF);
        payload[1] = (byte) (statusCode & 0xFF);
        System.arraycopy(reasonBytes, 0, payload, 2, reasonBytes.length);
        sendFrame(conn, 0x08, payload); // 0x08 = close
    }

    // ==================== Utility Methods ====================

    private String generateAcceptKey(String key) {
        try {
            String combined = key + WEBSOCKET_GUID;
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 not available", e);
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2) {
                    params.put(pair[0], pair[1]);
                }
            }
        }
        return params;
    }

    @Override
    public void close() {
        running.set(false);
        closeAllConnections();
        httpServer.stop(0);
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.info("TestWebSocketServer stopped");
    }

    // ==================== Inner Classes ====================

    private static class WebSocketFrame {
        final boolean fin;
        final int opcode;
        final byte[] payload;

        WebSocketFrame(boolean fin, int opcode, byte[] payload) {
            this.fin = fin;
            this.opcode = opcode;
            this.payload = payload;
        }
    }

    private static class WebSocketConnection {
        final String id;
        final String sessionId;
        final String requestId;
        final InputStream inputStream;
        final OutputStream outputStream;
        final Object outputLock = new Object();
        final AtomicBoolean running = new AtomicBoolean(true);
        final AtomicInteger messageCount = new AtomicInteger(0);

        WebSocketConnection(String id, String sessionId, String requestId, 
                          InputStream in, OutputStream out) {
            this.id = id;
            this.sessionId = sessionId;
            this.requestId = requestId;
            this.inputStream = in;
            this.outputStream = out;
        }
    }
}
