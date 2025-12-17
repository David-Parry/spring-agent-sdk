 /*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.service;

import com.davidparry.agent.core.api.StringConstants;
import com.davidparry.agent.core.api.TaskResponse;
import com.davidparry.agent.core.api.ToolData;
import com.davidparry.agent.core.pojo.CommandSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static com.davidparry.agent.core.service.WebSocketNotificationService.TYPE_STRUCTURED_OUTPUT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BaseHandler class, specifically testing the TYPE_STRUCTURED_OUTPUT field
 * and the handle method as the entry point.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BaseHandler - TYPE_STRUCTURED_OUTPUT Tests")
class BaseHandlerTest {

    @Mock
    private MessagePublisher messagePublisher;

    private ObjectMapper objectMapper;
    private TestBaseHandler testHandler;

    private static final String TEST_SESSION_ID = "test-session-123";
    private static final String TEST_REQUEST_ID = "test-request-456";
    private static final String TEST_EVENT_KEY = "test-event-key";
    private static final String TEST_CHECKPOINT_ID = "test-checkpoint-789";
    private static final String TEST_MESSAGE_TYPE = "test-message-type";
    private static final String TEST_HANDLER_TYPE = "test-handler-type";

    /**
     * Concrete implementation of BaseHandler for testing purposes.
     */
    private static class TestBaseHandler extends BaseHandler {
        private final String handlerType;

        public TestBaseHandler(MessagePublisher messagePublisher, ObjectMapper objectMapper, String handlerType) {
            super(messagePublisher, objectMapper);
            this.handlerType = handlerType;
        }

        @Override
        public String type() {
            return handlerType;
        }

        @Override
        public Map<String, Object> handle(Map<String, Object> map) {
            // Pass through without modification for basic tests
            return map;
        }
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        testHandler = new TestBaseHandler(messagePublisher, objectMapper, TEST_HANDLER_TYPE);
    }

    @Test
    @DisplayName("Should process structured output with success=true and set handler type")
    void testStructuredOutputWithSuccess() throws IOException {
        // Arrange - Load test data from file
        Path testDataPath = Paths.get("src/test/resources/structured-output-test-data.json");
        String structuredOutputJson = Files.readString(testDataPath);

        // Create TaskResponse with structured output in toolArgs
        Map<String, Object> toolArgs = Map.of(TYPE_STRUCTURED_OUTPUT, structuredOutputJson);

        ToolData toolData = new ToolData(
                null,  // serverName
                "ENDNODE",  // tool
                toolArgs,  // toolArgs
                null,  // toolReasoning
                null,  // identifier
                null,  // pendingApproval
                null,  // toolResult
                null,  // toolArgsForUi
                null,  // sessionId
                null   // checkpointId
        );

        TaskResponse taskResponse = new TaskResponse(
                TEST_SESSION_ID,
                toolData,
                TYPE_STRUCTURED_OUTPUT,  // type
                null,
                null,
                null
        );

        CommandSession commandSession = new CommandSession(
                TEST_MESSAGE_TYPE,
                TEST_SESSION_ID,
                null,
                TEST_EVENT_KEY,
                TEST_REQUEST_ID,
                null,
                null,
                0,
                null,
                TEST_CHECKPOINT_ID,
                null
        );

        // Act - Call handle method (entry point)
        testHandler.handle(commandSession, List.of(taskResponse));

        // Assert - Verify message was published
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagePublisher, times(1)).publishResponse(messageCaptor.capture());

        String publishedMessage = messageCaptor.getValue();
        assertNotNull(publishedMessage);

        // Parse the published message
        @SuppressWarnings("unchecked")
        Map<String, Object> publishedMap = objectMapper.readValue(publishedMessage, Map.class);

        // Verify core fields from CommandSession
        assertEquals(TEST_EVENT_KEY, publishedMap.get(StringConstants.EVENT_KEY.getValue()));
        assertEquals(TEST_SESSION_ID, publishedMap.get(StringConstants.SESSION_ID.getValue()));
        assertEquals(TEST_REQUEST_ID, publishedMap.get(StringConstants.REQUEST_ID.getValue()));
        assertEquals(TEST_CHECKPOINT_ID, publishedMap.get(StringConstants.CHECKPOINT_ID.getValue()));

        // Verify structured output fields
        assertEquals("SCRUM-427", publishedMap.get("issueKey"));
        assertEquals(true, publishedMap.get(StringConstants.SUCCESS.getValue()));
        assertEquals("design_complete", publishedMap.get("status"));
        assertEquals("https://github.com/David-Parry/vetpet-clinic-demo.git", publishedMap.get("git_repo_uri"));
        assertTrue(publishedMap.get("summary").toString().contains("SCRUM-427"));
        assertTrue(publishedMap.get("design").toString().contains("Implementation Design"));

        // Verify type is set to handler type when success=true
        assertEquals(TEST_HANDLER_TYPE, publishedMap.get(StringConstants.MESSAGE_TYPE.getValue()));
    }

    @Test
    @DisplayName("Duplicate properties structured output should process structured output with success=true and set handler type")
    void testDuplicatePropertiesStructuredOutputWithSuccess() throws IOException {
        // Arrange - Load test data from file
        Path testDataPath = Paths.get("src/test/resources/structured-output-test-data-duplicate-properties.json");
        String structuredOutputJson = Files.readString(testDataPath);

        // Create TaskResponse with structured output in toolArgs
        Map<String, Object> toolArgs = Map.of(TYPE_STRUCTURED_OUTPUT, structuredOutputJson);

        ToolData toolData = new ToolData(
                null,  // serverName
                "ENDNODE",  // tool
                toolArgs,  // toolArgs
                null,  // toolReasoning
                null,  // identifier
                null,  // pendingApproval
                null,  // toolResult
                null,  // toolArgsForUi
                null,  // sessionId
                null   // checkpointId
        );

        TaskResponse taskResponse = new TaskResponse(
                TEST_SESSION_ID,
                toolData,
                TYPE_STRUCTURED_OUTPUT,  // type
                null,
                null,
                null
        );

        CommandSession commandSession = new CommandSession(
                TEST_MESSAGE_TYPE,
                TEST_SESSION_ID,
                null,
                TEST_EVENT_KEY,
                TEST_REQUEST_ID,
                null,
                null,
                0,
                null,
                TEST_CHECKPOINT_ID,
                null
        );

        // Act - Call handle method (entry point)
        testHandler.handle(commandSession, List.of(taskResponse));

        // Assert - Verify message was published
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagePublisher, times(1)).publishResponse(messageCaptor.capture());

        String publishedMessage = messageCaptor.getValue();
        assertNotNull(publishedMessage);

        // Parse the published message
        @SuppressWarnings("unchecked")
        Map<String, Object> publishedMap = objectMapper.readValue(publishedMessage, Map.class);

        // Verify core fields from CommandSession
        assertEquals(TEST_EVENT_KEY, publishedMap.get(StringConstants.EVENT_KEY.getValue()));
        assertEquals(TEST_SESSION_ID, publishedMap.get(StringConstants.SESSION_ID.getValue()));
        assertEquals(TEST_REQUEST_ID, publishedMap.get(StringConstants.REQUEST_ID.getValue()));
        assertEquals(TEST_CHECKPOINT_ID, publishedMap.get(StringConstants.CHECKPOINT_ID.getValue()));

        // Verify structured output fields
        assertEquals("SCRUM-432", publishedMap.get("issueKey"));
        assertEquals(true, publishedMap.get(StringConstants.SUCCESS.getValue()));
        assertEquals("design_complete", publishedMap.get("status"));
        assertEquals("https://github.com/David-Parry/vetpet-clinic-demo.git", publishedMap.get("git_repo_uri"));
        assertTrue(publishedMap.get("summary").toString().contains("SCRUM-432"));
        assertTrue(publishedMap.get("design").toString().contains("Implementation Design"));

        // Verify type is set to handler type when success=true
        assertEquals(TEST_HANDLER_TYPE, publishedMap.get(StringConstants.MESSAGE_TYPE.getValue()));
    }


    @Test
    @DisplayName("Should set type to EndFlowCleanup.TYPE when success=false")
    void testStructuredOutputWithFailure() throws IOException {
        // Arrange - Load test data and modify success to false
        Path testDataPath = Paths.get("src/test/resources/structured-output-test-data.json");
        String structuredOutputJson = Files.readString(testDataPath);
        
        // Modify success to false
        @SuppressWarnings("unchecked")
        Map<String, Object> testData = objectMapper.readValue(structuredOutputJson, Map.class);
        testData.put("success", false);
        String modifiedJson = objectMapper.writeValueAsString(testData);

        // Create TaskResponse with modified structured output in toolArgs
        Map<String, Object> toolArgs = Map.of("output", modifiedJson);

        ToolData toolData = new ToolData(
                null,  // serverName
                "structured_output",  // tool
                toolArgs,  // toolArgs
                null,  // toolReasoning
                null,  // identifier
                null,  // pendingApproval
                null,  // toolResult
                null,  // toolArgsForUi
                null,  // sessionId
                null   // checkpointId
        );

        TaskResponse taskResponse = new TaskResponse(
                TEST_SESSION_ID,
                toolData,
                TYPE_STRUCTURED_OUTPUT,  // type
                null,
                null,
                null
        );

        CommandSession commandSession = new CommandSession(
                TEST_MESSAGE_TYPE,
                TEST_SESSION_ID,
                null,
                TEST_EVENT_KEY,
                TEST_REQUEST_ID,
                null,
                null,
                0,
                null,
                TEST_CHECKPOINT_ID,
                null
        );

        // Act
        testHandler.handle(commandSession, List.of(taskResponse));

        // Assert
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagePublisher, times(1)).publishResponse(messageCaptor.capture());

        String publishedMessage = messageCaptor.getValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> publishedMap = objectMapper.readValue(publishedMessage, Map.class);

        // Verify type is set to EndFlowCleanup.TYPE when success=false
        assertEquals(EndFlowCleanup.TYPE, publishedMap.get(StringConstants.MESSAGE_TYPE.getValue()));
        assertEquals(false, publishedMap.get(StringConstants.SUCCESS.getValue()));
    }

    @Test
    @DisplayName("Should set type to INCOMPLETE_NODE when JSON parsing fails")
    void testStructuredOutputWithInvalidJson() {
        // Arrange - Create invalid JSON
        String invalidJson = "{ invalid json content }";

        // Create TaskResponse with invalid JSON in toolArgs
        Map<String, Object> toolArgs = Map.of("output", invalidJson);

        ToolData toolData = new ToolData(
                null,  // serverName
                "structured_output",  // tool
                toolArgs,  // toolArgs
                null,  // toolReasoning
                null,  // identifier
                null,  // pendingApproval
                null,  // toolResult
                null,  // toolArgsForUi
                null,  // sessionId
                null   // checkpointId
        );

        TaskResponse taskResponse = new TaskResponse(
                TEST_SESSION_ID,
                toolData,
                TYPE_STRUCTURED_OUTPUT,  // type
                null,
                null,
                null
        );

        CommandSession commandSession = new CommandSession(
                TEST_MESSAGE_TYPE,
                TEST_SESSION_ID,
                null,
                TEST_EVENT_KEY,
                TEST_REQUEST_ID,
                null,
                null,
                0,
                null,
                TEST_CHECKPOINT_ID,
                null
        );

        // Act
        testHandler.handle(commandSession, List.of(taskResponse));

        // Assert
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagePublisher, times(1)).publishResponse(messageCaptor.capture());

        String publishedMessage = messageCaptor.getValue();
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> publishedMap = objectMapper.readValue(publishedMessage, Map.class);
            
            // Verify type is set to INCOMPLETE_NODE when parsing fails
            assertEquals(MessageService.INCOMPLETE_NODE, publishedMap.get(StringConstants.MESSAGE_TYPE.getValue()));
            
            // Verify LLM conversation is included
            assertTrue(publishedMap.containsKey(StringConstants.LLM_CONVERSATION.getValue()));
        } catch (IOException e) {
            fail("Published message should be valid JSON: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Should verify TYPE_STRUCTURED_OUTPUT constant value")
    void testTypeStructuredOutputConstant() {
        // Assert - Verify the constant value
        assertEquals("structured_output", TYPE_STRUCTURED_OUTPUT);
    }

    @Test
    @DisplayName("Should handle multiple TaskResponses and extract structured output")
    void testMultipleTaskResponses() throws IOException {
        // Arrange - Load test data
        Path testDataPath = Paths.get("src/test/resources/structured-output-test-data.json");
        String structuredOutputJson = Files.readString(testDataPath);

        // Create multiple TaskResponses, only one with structured output
        Map<String, Object> structuredToolArgs = Map.of("output", structuredOutputJson);

        ToolData structuredToolData = new ToolData(
                null,  // serverName
                "structured_output",  // tool
                structuredToolArgs,  // toolArgs
                null,  // toolReasoning
                null,  // identifier
                null,  // pendingApproval
                null,  // toolResult
                null,  // toolArgsForUi
                null,  // sessionId
                null   // checkpointId
        );

        Map<String, Object> otherToolArgs = Map.of("output", "Some other content");

        ToolData otherToolData = new ToolData(
                null,  // serverName
                "other_tool",  // tool
                otherToolArgs,  // toolArgs
                null,  // toolReasoning
                null,  // identifier
                null,  // pendingApproval
                null,  // toolResult
                null,  // toolArgsForUi
                null,  // sessionId
                null   // checkpointId
        );

        TaskResponse structuredResponse = new TaskResponse(
                TEST_SESSION_ID,
                structuredToolData,
                TYPE_STRUCTURED_OUTPUT,  // type
                null,
                null,
                null
        );

        TaskResponse otherResponse = new TaskResponse(
                TEST_SESSION_ID,
                otherToolData,
                "other_type",  // type
                null,
                null,
                null
        );

        CommandSession commandSession = new CommandSession(
                TEST_MESSAGE_TYPE,
                TEST_SESSION_ID,
                null,
                TEST_EVENT_KEY,
                TEST_REQUEST_ID,
                null,
                null,
                0,
                null,
                TEST_CHECKPOINT_ID,
                null
        );

        // Act
        testHandler.handle(commandSession, List.of(otherResponse, structuredResponse));

        // Assert
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagePublisher, times(1)).publishResponse(messageCaptor.capture());

        String publishedMessage = messageCaptor.getValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> publishedMap = objectMapper.readValue(publishedMessage, Map.class);

        // Verify structured output was extracted correctly
        assertEquals("SCRUM-427", publishedMap.get("issueKey"));
        assertEquals(true, publishedMap.get(StringConstants.SUCCESS.getValue()));
        assertEquals(TEST_HANDLER_TYPE, publishedMap.get(StringConstants.MESSAGE_TYPE.getValue()));
    }

    @Test
    @DisplayName("Should include project structure in published message")
    void testProjectStructureIncluded() throws IOException {
        // Arrange
        Path testDataPath = Paths.get("src/test/resources/structured-output-test-data.json");
        String structuredOutputJson = Files.readString(testDataPath);

        Map<String, Object> toolArgs = Map.of("output", structuredOutputJson);

        ToolData toolData = new ToolData(
                null,  // serverName
                "structured_output",  // tool
                toolArgs,  // toolArgs
                null,  // toolReasoning
                null,  // identifier
                null,  // pendingApproval
                null,  // toolResult
                null,  // toolArgsForUi
                null,  // sessionId
                null   // checkpointId
        );

        TaskResponse taskResponse = new TaskResponse(
                TEST_SESSION_ID,
                toolData,
                TYPE_STRUCTURED_OUTPUT,  // type
                null,
                null,
                null
        );

        CommandSession commandSession = new CommandSession(
                TEST_MESSAGE_TYPE,
                TEST_SESSION_ID,
                null,
                TEST_EVENT_KEY,
                TEST_REQUEST_ID,
                null,
                null,
                0,
                null,
                TEST_CHECKPOINT_ID,
                null
        );

        // Act
        testHandler.handle(commandSession, List.of(taskResponse));

        // Assert
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagePublisher, times(1)).publishResponse(messageCaptor.capture());

        String publishedMessage = messageCaptor.getValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> publishedMap = objectMapper.readValue(publishedMessage, Map.class);

        // Verify project structure is included
        assertTrue(publishedMap.containsKey(StringConstants.PROJECT_STRUCTURE.getValue()));
        assertNotNull(publishedMap.get(StringConstants.PROJECT_STRUCTURE.getValue()));
    }

    @Test
    @DisplayName("Should handle custom handler implementation that modifies the map")
    void testCustomHandlerModification() throws IOException {
        // Arrange - Create custom handler that adds a field
        TestBaseHandler customHandler = new TestBaseHandler(messagePublisher, objectMapper, TEST_HANDLER_TYPE) {
            @Override
            public Map<String, Object> handle(Map<String, Object> map) {
                map.put("customField", "customValue");
                return map;
            }
        };

        Path testDataPath = Paths.get("src/test/resources/structured-output-test-data.json");
        String structuredOutputJson = Files.readString(testDataPath);

        Map<String, Object> toolArgs = Map.of("output", structuredOutputJson);

        ToolData toolData = new ToolData(
                null,  // serverName
                "structured_output",  // tool
                toolArgs,  // toolArgs
                null,  // toolReasoning
                null,  // identifier
                null,  // pendingApproval
                null,  // toolResult
                null,  // toolArgsForUi
                null,  // sessionId
                null   // checkpointId
        );

        TaskResponse taskResponse = new TaskResponse(
                TEST_SESSION_ID,
                toolData,
                TYPE_STRUCTURED_OUTPUT,  // type
                null,
                null,
                null
        );

        CommandSession commandSession = new CommandSession(
                TEST_MESSAGE_TYPE,
                TEST_SESSION_ID,
                null,
                TEST_EVENT_KEY,
                TEST_REQUEST_ID,
                null,
                null,
                0,
                null,
                TEST_CHECKPOINT_ID,
                null
        );

        // Act
        customHandler.handle(commandSession, List.of(taskResponse));

        // Assert
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagePublisher, times(1)).publishResponse(messageCaptor.capture());

        String publishedMessage = messageCaptor.getValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> publishedMap = objectMapper.readValue(publishedMessage, Map.class);

        // Verify custom field was added
        assertEquals("customValue", publishedMap.get("customField"));
        assertEquals(true, publishedMap.get(StringConstants.SUCCESS.getValue()));
    }
}
