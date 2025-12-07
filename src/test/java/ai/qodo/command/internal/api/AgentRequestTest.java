/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class AgentRequestTest {
    
    private ObjectMapper objectMapper;
    private BaseData baseData;
    private TaskBaseData taskBaseData;
    private TaskRequestData taskRequestData;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        
        UserDataRequest userData = new UserDataRequest("1.0.0", "linux", "5.15", "vscode");
        Map<String, List<Map<String, Object>>> tools = new HashMap<>();
        
        baseData = new BaseData(
            "session-123",
            userData,
            "test-agent",
            tools,
            "gpt-4",
            "read-write"
        );
        
        taskBaseData = new TaskBaseData(
            List.of("/project/root"),
            "project structure",
            "instructions",
            "system prompt",
            "/current/dir"
        );
        
        taskRequestData = new TaskRequestData(
            "user request",
            Map.of("param1", "value1"),
            null,
            null,
            null,
            "test-tool",
            "tool-id-456",
            null,
            null,
            null,
            null,
            null,
            "git-sha-123"
        );
    }
    
    @Test
    void testAgentRequestCreation() {
        AgentRequest request = new AgentRequest(baseData, taskBaseData, taskRequestData);
        
        assertNotNull(request, "AgentRequest should not be null");
        assertEquals(baseData, request.baseData(), "BaseData should match");
        assertEquals(taskBaseData, request.taskBaseData(), "TaskBaseData should match");
        assertEquals(taskRequestData, request.taskRequestData(), "TaskRequestData should match");
    }
    
    @Test
    void testAgentRequestEquality() {
        AgentRequest request1 = new AgentRequest(baseData, taskBaseData, taskRequestData);
        AgentRequest request2 = new AgentRequest(baseData, taskBaseData, taskRequestData);
        
        assertEquals(request1, request2, "AgentRequests with same data should be equal");
        assertEquals(request1.hashCode(), request2.hashCode(), "Hash codes should match for equal objects");
    }
    
    @Test
    void testAgentRequestToString() {
        AgentRequest request = new AgentRequest(baseData, taskBaseData, taskRequestData);
        String stringRepresentation = request.toString();
        
        assertNotNull(stringRepresentation, "toString should not return null");
        assertTrue(stringRepresentation.contains("AgentRequest"), "toString should contain record name");
    }
    
    @Test
    void testAgentRequestWithNullValues() {
        AgentRequest request = new AgentRequest(null, null, null);
        
        assertNull(request.baseData(), "BaseData can be null");
        assertNull(request.taskBaseData(), "TaskBaseData can be null");
        assertNull(request.taskRequestData(), "TaskRequestData can be null");
    }
    
    @Test
    void testJsonUnwrappedSerialization() throws Exception {
        AgentRequest request = new AgentRequest(baseData, taskBaseData, taskRequestData);
        String json = objectMapper.writeValueAsString(request);
        
        assertNotNull(json, "JSON should not be null");
        assertTrue(json.contains("session-123"), "JSON should contain sessionId from baseData");
        assertTrue(json.contains("git-sha-123"), "JSON should contain gitSha1 from taskBaseData");
        assertTrue(json.contains("test-tool"), "JSON should contain tool from taskRequestData");
        assertFalse(json.contains("baseData"), "JSON should not contain 'baseData' wrapper");
        assertFalse(json.contains("taskBaseData"), "JSON should not contain 'taskBaseData' wrapper");
        assertFalse(json.contains("taskRequestData"), "JSON should not contain 'taskRequestData' wrapper");
    }
}
