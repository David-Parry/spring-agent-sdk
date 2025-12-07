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

class AgentRequestBuilderTest {
    
    private AgentRequestBuilder builder;
    
    @BeforeEach
    void setUp() {
        builder = new AgentRequestBuilder();
    }
    
    @Test
    void testBuilderCreation() {
        assertNotNull(builder, "Builder should not be null");
        assertNotNull(builder.baseData(), "BaseData builder should not be null");
        assertNotNull(builder.taskBaseData(), "TaskBaseData builder should not be null");
        assertNotNull(builder.taskRequestData(), "TaskRequestData builder should not be null");
    }
    
    @Test
    void testBuildWithAllFields() {
        AgentRequest request = builder
            .sessionId("session-123")
            .tool("test-tool")
            .build();
        
        assertNotNull(request, "AgentRequest should not be null");
        assertNotNull(request.baseData(), "BaseData should not be null");
        assertNotNull(request.taskBaseData(), "TaskBaseData should not be null");
        assertNotNull(request.taskRequestData(), "TaskRequestData should not be null");
        assertEquals("session-123", request.baseData().sessionId());
        assertEquals("test-tool", request.taskRequestData().tool());
    }
    
    @Test
    void testDelegationToBaseDataBuilder() {
        builder.sessionId("session-456");
        
        BaseData baseData = builder.baseData().build();
        
        assertEquals("session-456", baseData.sessionId(), "SessionId should be delegated to BaseDataBuilder");
    }
    
    @Test
    void testDelegationToTaskRequestDataBuilder() {
        builder.tool("my-tool");
        
        TaskRequestData taskRequestData = builder.taskRequestData().build();
        
        assertEquals("my-tool", taskRequestData.tool(), "Tool should be delegated to TaskRequestDataBuilder");
    }
    
    @Test
    void testDirectAccessToSubBuilders() {
        builder.baseData().sessionId("direct-session");
        builder.taskBaseData().cwd("/direct/cwd");
        builder.taskRequestData().tool("direct-tool");
        
        AgentRequest request = builder.build();
        
        assertEquals("direct-session", request.baseData().sessionId());
        assertEquals("/direct/cwd", request.taskBaseData().cwd());
        assertEquals("direct-tool", request.taskRequestData().tool());
    }
    
    @Test
    void testFluentApiChaining() {
        AgentRequest request = builder
            .sessionId("chain-session")
            .tool("chain-tool")
            .build();
        
        assertNotNull(request, "Fluent API should return valid AgentRequest");
        assertEquals("chain-session", request.baseData().sessionId());
        assertEquals("chain-tool", request.taskRequestData().tool());
    }
    
    @Test
    void testBuildWithPartialConfiguration() {
        AgentRequest request = builder
            .sessionId("partial-session")
            .build();
        
        assertNotNull(request, "AgentRequest should be built with partial configuration");
        assertEquals("partial-session", request.baseData().sessionId());
        assertNull(request.taskRequestData().tool(), "Unconfigured fields should be null");
    }
    
    @Test
    void testBuildWithNoConfiguration() {
        AgentRequest request = builder.build();
        
        assertNotNull(request, "AgentRequest should be built even with no configuration");
        assertNotNull(request.baseData(), "BaseData should not be null");
        assertNotNull(request.taskBaseData(), "TaskBaseData should not be null");
        assertNotNull(request.taskRequestData(), "TaskRequestData should not be null");
    }
    
    @Test
    void testMultipleBuildCalls() {
        builder.sessionId("session-1").tool("tool-1");
        AgentRequest request1 = builder.build();
        
        builder.sessionId("session-2").tool("tool-2");
        AgentRequest request2 = builder.build();
        
        // Both builds should use the same builder state (last configuration)
        assertEquals("session-2", request2.baseData().sessionId());
        assertEquals("tool-2", request2.taskRequestData().tool());
    }
}
