/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class BaseDataTest {
    
    private BaseData baseData;
    private UserDataRequest userData;
    private Map<String, List<Map<String, Object>>> tools;
    
    @BeforeEach
    void setUp() {
        // Create test data for UserDataRequest (assuming it has a no-arg constructor or create with required fields)
        userData = null; // We'll use null for simplicity in this test
        tools = new HashMap<>();
        
        baseData = new BaseData(
            "test-session-123",
            userData,
            "test-agent",
            tools,
            "gpt-4",
            "read-write"
        );
    }
    
    @Test
    void testSessionId() {
        BaseData baseData1 = new BaseData(  "test-session-123",
                                            userData,
                                            "test-agent",
                                            tools,
                                            "gpt-4",
                                            "read-write");
        assertEquals("test-session-123", baseData1.sessionId(), "Session ID should match");
    }
    
    @Test
    void testAgentType() {
        assertEquals("test-agent", baseData.agentType(), "Agent type should match");
    }
    
    @Test
    void testCustomModel() {
        assertEquals("gpt-4", baseData.customModel(), "Custom model should match");
    }
    
    @Test
    void testPermissions() {
        assertEquals("read-write", baseData.permissions(), "Permissions should match");
    }
    
    @Test
    void testTools() {
        assertNotNull(baseData.tools(), "Tools should not be null");
        assertTrue(baseData.tools().isEmpty(), "Tools should be empty initially");
    }
    
    @Test
    void testRecordEquality() {
        BaseData sameData = new BaseData(
            "test-session-123",
            userData,
            "test-agent",
            tools,
            "gpt-4",
            "read-write"
        );
        
        assertEquals(baseData, sameData, "Records with same values should be equal");
        assertEquals(baseData.hashCode(), sameData.hashCode(), "Hash codes should match for equal records");
    }
    
    @Test
    void testRecordToString() {
        String stringRepresentation = baseData.toString();
        assertNotNull(stringRepresentation, "toString should not return null");
        assertTrue(stringRepresentation.contains("BaseData"), "toString should contain record name");
        assertTrue(stringRepresentation.contains("test-session-123"), "toString should contain session ID");
    }
    
    @Test
    void testWithNullValues() {
        BaseData dataWithNulls = new BaseData(null, null, null, null, null, null);
        assertNull(dataWithNulls.sessionId(), "Session ID can be null");
        assertNull(dataWithNulls.userData(), "User data can be null");
        assertNull(dataWithNulls.agentType(), "Agent type can be null");
        assertNull(dataWithNulls.tools(), "Tools can be null");
        assertNull(dataWithNulls.customModel(), "Custom model can be null");
        assertNull(dataWithNulls.permissions(), "Permissions can be null");
    }
}