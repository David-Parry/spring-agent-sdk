/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ToolResponseTest {
    
    private ObjectMapper objectMapper;
    private UserDataRequest userData;
    private Map<String, List<McpSchema.Tool>> tools;
    private ToolAnswer answer;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        userData = new UserDataRequest("1.0.0", "linux", "5.15", "vscode");
        tools = new HashMap<>();
        
        List<ToolResponseContent> content = List.of(
            new ToolResponseContent("text", "Success")
        );
        answer = new ToolAnswer(false, content);
    }
    
    @Test
    void testToolResponseCreation() {
        ToolResponse response = new ToolResponse(
            "test-agent",
            "session-123",
            userData,
            "git-sha-123",
            tools,
            "read-write",
            List.of("/project/root"),
            "/current/dir",
            "test-tool",
            "tool-id-456",
            answer
        );
        
        assertNotNull(response, "ToolResponse should not be null");
        assertEquals("test-agent", response.agentType());
        assertEquals("session-123", response.sessionId());
        assertEquals(userData, response.userData());
        assertEquals("git-sha-123", response.gitSha1());
        assertEquals(tools, response.tools());
        assertEquals("read-write", response.permissions());
        assertEquals(List.of("/project/root"), response.projectsRootPath());
        assertEquals("/current/dir", response.cwd());
        assertEquals("test-tool", response.tool());
        assertEquals("tool-id-456", response.toolId());
        assertEquals(answer, response.answer());
    }
    
    @Test
    void testToolResponseEquality() {
        ToolResponse response1 = new ToolResponse(
            "test-agent", "session-123", userData, "git-sha-123", tools,
            "read-write", List.of("/project/root"), "/current/dir",
            "test-tool", "tool-id-456", answer
        );
        
        ToolResponse response2 = new ToolResponse(
            "test-agent", "session-123", userData, "git-sha-123", tools,
            "read-write", List.of("/project/root"), "/current/dir",
            "test-tool", "tool-id-456", answer
        );
        
        assertEquals(response1, response2, "ToolResponses with same data should be equal");
        assertEquals(response1.hashCode(), response2.hashCode(), "Hash codes should match");
    }
    
    @Test
    void testJsonSerializationWithNonNullInclusion() throws Exception {
        ToolResponse response = new ToolResponse(
            "test-agent", "session-123", null, null, null,
            null, null, null, "test-tool", null, null
        );
        
        String json = objectMapper.writeValueAsString(response);
        
        assertNotNull(json, "JSON should not be null");
        assertTrue(json.contains("agent_type"), "JSON should contain agent_type");
        assertTrue(json.contains("session_id"), "JSON should contain session_id");
        assertTrue(json.contains("tool"), "JSON should contain tool");
        assertFalse(json.contains("user_data"), "JSON should not contain null user_data");
        assertFalse(json.contains("git_sha1"), "JSON should not contain null git_sha1");
    }
    
    @Test
    void testJsonPropertyNaming() throws Exception {
        ToolResponse response = new ToolResponse(
            "test-agent", "session-123", userData, "git-sha-123", tools,
            "read-write", List.of("/project/root"), "/current/dir",
            "test-tool", "tool-id-456", answer
        );
        
        String json = objectMapper.writeValueAsString(response);
        
        assertTrue(json.contains("agent_type"), "JSON should use snake_case for agentType");
        assertTrue(json.contains("session_id"), "JSON should use snake_case for sessionId");
        assertTrue(json.contains("user_data"), "JSON should use snake_case for userData");
        assertTrue(json.contains("git_sha1"), "JSON should use snake_case for gitSha1");
        assertTrue(json.contains("projects_root_path"), "JSON should use snake_case for projectsRootPath");
        assertTrue(json.contains("tool_id"), "JSON should use snake_case for toolId");
    }
    
    @Test
    void testOptionalProjectsRootPath() {
        ToolResponse withPath = new ToolResponse(
            "test-agent", "session-123", userData, "git-sha-123", tools,
            "read-write", List.of("/project/root"), "/current/dir",
            "test-tool", "tool-id-456", answer
        );
        
        ToolResponse withoutPath = new ToolResponse(
            "test-agent", "session-123", userData, "git-sha-123", tools,
            "read-write", null, "/current/dir",
            "test-tool", "tool-id-456", answer
        );
        
        assertNotNull(withPath.projectsRootPath(), "projectsRootPath should not be null when provided");
        assertNull(withoutPath.projectsRootPath(), "projectsRootPath should be null when not provided");
    }
    
    @Test
    void testJsonIgnoreUnknownProperties() throws Exception {
        String jsonWithUnknownFields = """
            {
                "agent_type": "test-agent",
                "session_id": "session-123",
                "tool": "test-tool",
                "unknown_field": "should be ignored",
                "another_unknown": 123
            }
            """;
        
        ToolResponse response = objectMapper.readValue(jsonWithUnknownFields, ToolResponse.class);
        
        assertNotNull(response, "ToolResponse should be created despite unknown fields");
        assertEquals("test-agent", response.agentType());
        assertEquals("session-123", response.sessionId());
        assertEquals("test-tool", response.tool());
    }
    
    @Test
    void testToolResponseWithAllNullValues() {
        ToolResponse response = new ToolResponse(
            null, null, null, null, null, null, null, null, null, null, null
        );
        
        assertNull(response.agentType());
        assertNull(response.sessionId());
        assertNull(response.userData());
        assertNull(response.gitSha1());
        assertNull(response.tools());
        assertNull(response.permissions());
        assertNull(response.projectsRootPath());
        assertNull(response.cwd());
        assertNull(response.tool());
        assertNull(response.toolId());
        assertNull(response.answer());
    }
}
