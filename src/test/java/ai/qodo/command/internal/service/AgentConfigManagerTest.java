/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.service;

import ai.qodo.command.internal.mcp.AgentCommand;
import ai.qodo.command.internal.mcp.AgentConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AgentConfigManager.loadAgentConfiguration() method.
 * Tests use the agent.yml file from test resources directory.
 * 
 * This test class mocks the AgentConfigService to avoid Spring context initialization,
 * but the actual agent.yml file in src/test/resources is used as the basis for test data.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgentConfigManager.loadAgentConfiguration() Tests")
class AgentConfigManagerTest {

    @Mock
    private AgentConfigService agentConfigService;

    private AgentConfigManager agentConfigManager;

    @BeforeEach
    void setUp() {
        agentConfigManager = new AgentConfigManager(agentConfigService);
    }

    @Test
    @DisplayName("Should successfully load agent configuration with valid agent.yml")
    void testLoadAgentConfiguration_Success() throws Exception {
        // Arrange
        String configFilePath = "classpath:agent.yml";
        AgentConfig mockAgentConfig = createMockAgentConfig();

        when(agentConfigService.getConfigFilePath()).thenReturn(configFilePath);
        when(agentConfigService.loadAgentConfig()).thenReturn(mockAgentConfig);

        // Act
        agentConfigManager.loadAgentConfiguration();

        // Assert
        verify(agentConfigService, times(1)).getConfigFilePath();
        verify(agentConfigService, times(1)).loadAgentConfig();
        
        AgentConfig loadedConfig = agentConfigManager.getAgentConfig();
        assertNotNull(loadedConfig, "Loaded agent config should not be null");
        assertEquals("1.0", loadedConfig.version(), "Version should match");
        assertEquals(3, loadedConfig.commands().size(), "Should have 3 commands");
        assertTrue(loadedConfig.commands().containsKey("snyk_agent"), "Should contain snyk_agent command");
        assertTrue(loadedConfig.commands().containsKey("jira_agent"), "Should contain jira_agent command");
        assertTrue(loadedConfig.commands().containsKey("coding_agent"), "Should contain coding_agent command");
    }

    @Test
    @DisplayName("Should throw RuntimeException when agent config file is not found")
    void testLoadAgentConfiguration_FileNotFound() throws Exception {
        // Arrange
        String configFilePath = "classpath:agent.yml";
        IOException ioException = new IOException("Agent configuration file not found: " + configFilePath);

        when(agentConfigService.getConfigFilePath()).thenReturn(configFilePath);
        when(agentConfigService.loadAgentConfig()).thenThrow(ioException);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> agentConfigManager.loadAgentConfiguration(),
            "Should throw RuntimeException when file is not found");

        assertTrue(exception.getMessage().contains("Failed to load agent configuration"),
            "Exception message should indicate configuration load failure");
        assertEquals(ioException, exception.getCause(), "Cause should be the IOException");
        
        verify(agentConfigService, times(1)).getConfigFilePath();
        verify(agentConfigService, times(1)).loadAgentConfig();
        
        assertNull(agentConfigManager.getAgentConfig(), 
            "Agent config should be null when loading fails");
    }

    @Test
    @DisplayName("Should throw RuntimeException when agent config parsing fails")
    void testLoadAgentConfiguration_ParsingError() throws Exception {
        // Arrange
        String configFilePath = "classpath:agent.yml";
        Exception parsingException = new Exception("Failed to parse YAML: Invalid format");

        when(agentConfigService.getConfigFilePath()).thenReturn(configFilePath);
        when(agentConfigService.loadAgentConfig()).thenThrow(parsingException);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> agentConfigManager.loadAgentConfiguration(),
            "Should throw RuntimeException when parsing fails");

        assertTrue(exception.getMessage().contains("Failed to load agent configuration"),
            "Exception message should indicate configuration load failure");
        assertTrue(exception.getMessage().contains("Cannot continue till agents are valid"),
            "Exception message should indicate agents must be valid");
        assertEquals(parsingException, exception.getCause(), "Cause should be the parsing exception");
        
        verify(agentConfigService, times(1)).loadAgentConfig();
        
        assertNull(agentConfigManager.getAgentConfig(),
            "Agent config should be null when parsing fails");
    }

    @Test
    @DisplayName("Should throw RuntimeException when output schema is invalid")
    void testLoadAgentConfiguration_InvalidOutputSchema() throws Exception {
        // Arrange
        String configFilePath = "classpath:agent.yml";
        Exception schemaException = new Exception("Output schema is invalid and will not work with Agent calls");

        when(agentConfigService.getConfigFilePath()).thenReturn(configFilePath);
        when(agentConfigService.loadAgentConfig()).thenThrow(schemaException);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> agentConfigManager.loadAgentConfiguration(),
            "Should throw RuntimeException when output schema is invalid");

        assertTrue(exception.getMessage().contains("Failed to load agent configuration"),
            "Exception message should indicate configuration load failure");
        assertEquals(schemaException, exception.getCause(), "Cause should be the schema exception");
        
        verify(agentConfigService, times(1)).loadAgentConfig();
        
        assertNull(agentConfigManager.getAgentConfig(),
            "Agent config should be null when schema validation fails");
    }

    @Test
    @DisplayName("Should return null when getAgentConfig is called before loadAgentConfiguration")
    void testGetAgentConfig_BeforeLoading() {
        // Act
        AgentConfig config = agentConfigManager.getAgentConfig();

        // Assert
        assertNull(config, "Agent config should be null before loading");
        verifyNoInteractions(agentConfigService);
    }

    @Test
    @DisplayName("Should successfully load agent configuration with empty commands map")
    void testLoadAgentConfiguration_EmptyCommands() throws Exception {
        // Arrange
        String configFilePath = "classpath:agent.yml";
        AgentConfig emptyConfig = new AgentConfig("1.0", "System prompt", new HashMap<>());

        when(agentConfigService.getConfigFilePath()).thenReturn(configFilePath);
        when(agentConfigService.loadAgentConfig()).thenReturn(emptyConfig);

        // Act
        agentConfigManager.loadAgentConfiguration();

        // Assert
        AgentConfig loadedConfig = agentConfigManager.getAgentConfig();
        assertNotNull(loadedConfig, "Loaded agent config should not be null");
        assertEquals(0, loadedConfig.commands().size(), "Should have 0 commands");
        
        verify(agentConfigService, times(1)).loadAgentConfig();
    }

    @Test
    @DisplayName("Should handle null pointer exception gracefully")
    void testLoadAgentConfiguration_NullPointerException() throws Exception {
        // Arrange
        String configFilePath = "classpath:agent.yml";
        NullPointerException npe = new NullPointerException("Unexpected null value in configuration");

        when(agentConfigService.getConfigFilePath()).thenReturn(configFilePath);
        when(agentConfigService.loadAgentConfig()).thenThrow(npe);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> agentConfigManager.loadAgentConfiguration(),
            "Should throw RuntimeException when NPE occurs");

        assertEquals(npe, exception.getCause(), "Cause should be the NullPointerException");
        
        assertNull(agentConfigManager.getAgentConfig(),
            "Agent config should be null when NPE occurs");
    }

    @Test
    @DisplayName("Should verify correct logging of configuration details")
    void testLoadAgentConfiguration_LoggingVerification() throws Exception {
        // Arrange
        String configFilePath = "classpath:agent.yml";
        AgentConfig mockAgentConfig = createMockAgentConfig();

        when(agentConfigService.getConfigFilePath()).thenReturn(configFilePath);
        when(agentConfigService.loadAgentConfig()).thenReturn(mockAgentConfig);

        // Act
        agentConfigManager.loadAgentConfiguration();

        // Assert - verify service methods were called in correct order
        var inOrder = inOrder(agentConfigService);
        inOrder.verify(agentConfigService).getConfigFilePath();
        inOrder.verify(agentConfigService).loadAgentConfig();
        
        AgentConfig loadedConfig = agentConfigManager.getAgentConfig();
        assertNotNull(loadedConfig);
        assertEquals("1.0", loadedConfig.version());
    }

    @Test
    @DisplayName("Should verify system prompt is loaded correctly")
    void testLoadAgentConfiguration_SystemPrompt() throws Exception {
        // Arrange
        String configFilePath = "classpath:agent.yml";
        AgentConfig mockAgentConfig = createMockAgentConfig();

        when(agentConfigService.getConfigFilePath()).thenReturn(configFilePath);
        when(agentConfigService.loadAgentConfig()).thenReturn(mockAgentConfig);

        // Act
        agentConfigManager.loadAgentConfiguration();

        // Assert
        AgentConfig loadedConfig = agentConfigManager.getAgentConfig();
        assertNotNull(loadedConfig.systemPrompt(), "System prompt should not be null");
        assertEquals("You are a Distinguished Software Engineer performing software development tasks.", 
                     loadedConfig.systemPrompt(), 
                     "System prompt should match agent.yml");
    }

    @Test
    @DisplayName("Should verify all three commands from agent.yml are loaded")
    void testLoadAgentConfiguration_AllCommandsPresent() throws Exception {
        // Arrange
        String configFilePath = "classpath:agent.yml";
        AgentConfig mockAgentConfig = createMockAgentConfig();

        when(agentConfigService.getConfigFilePath()).thenReturn(configFilePath);
        when(agentConfigService.loadAgentConfig()).thenReturn(mockAgentConfig);

        // Act
        agentConfigManager.loadAgentConfiguration();

        // Assert
        AgentConfig loadedConfig = agentConfigManager.getAgentConfig();
        Map<String, AgentCommand> commands = loadedConfig.commands();
        
        assertNotNull(commands, "Commands map should not be null");
        assertEquals(3, commands.size(), "Should have exactly 3 commands");
        
        // Verify snyk_agent
        assertTrue(commands.containsKey("snyk_agent"), "Should contain snyk_agent");
        AgentCommand snykAgent = commands.get("snyk_agent");
        assertNotNull(snykAgent, "snyk_agent should not be null");
        
        // Verify jira_agent
        assertTrue(commands.containsKey("jira_agent"), "Should contain jira_agent");
        AgentCommand jiraAgent = commands.get("jira_agent");
        assertNotNull(jiraAgent, "jira_agent should not be null");
        
        // Verify bug_coding_agent
        assertTrue(commands.containsKey("coding_agent"), "Should contain coding_agent");
        AgentCommand bugCodingAgent = commands.get("coding_agent");
        assertNotNull(bugCodingAgent, "coding_agent should not be null");
    }

    @Test
    @DisplayName("Should handle generic Exception during loading")
    void testLoadAgentConfiguration_GenericException() throws Exception {
        // Arrange
        String configFilePath = "classpath:agent.yml";
        Exception genericException = new Exception("Unexpected error occurred");

        when(agentConfigService.getConfigFilePath()).thenReturn(configFilePath);
        when(agentConfigService.loadAgentConfig()).thenThrow(genericException);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> agentConfigManager.loadAgentConfiguration(),
            "Should throw RuntimeException for generic exceptions");

        assertTrue(exception.getMessage().contains("Failed to load agent configuration"),
            "Exception message should indicate configuration load failure");
        assertEquals(genericException, exception.getCause(), "Cause should be the generic exception");
        
        assertNull(agentConfigManager.getAgentConfig(),
            "Agent config should be null when exception occurs");
    }

    @Test
    @DisplayName("Should verify getAgentConfig returns same instance after loading")
    void testGetAgentConfig_ReturnsSameInstance() throws Exception {
        // Arrange
        String configFilePath = "classpath:agent.yml";
        AgentConfig mockAgentConfig = createMockAgentConfig();

        when(agentConfigService.getConfigFilePath()).thenReturn(configFilePath);
        when(agentConfigService.loadAgentConfig()).thenReturn(mockAgentConfig);

        // Act
        agentConfigManager.loadAgentConfiguration();
        AgentConfig firstCall = agentConfigManager.getAgentConfig();
        AgentConfig secondCall = agentConfigManager.getAgentConfig();

        // Assert
        assertSame(firstCall, secondCall, "getAgentConfig should return the same instance");
        verify(agentConfigService, times(1)).loadAgentConfig(); // Should only load once
    }

    /**
     * Helper method to create a mock AgentConfig that matches the test agent.yml structure.
     * This simulates the data that would be loaded from src/test/resources/agent.yml
     */
    private AgentConfig createMockAgentConfig() {
        Map<String, AgentCommand> commands = new HashMap<>();
        
        // Create mock commands matching the agent.yml structure
        // Using lenient() to avoid UnnecessaryStubbingException for unused stubs
        AgentCommand snykAgent = mock(AgentCommand.class, withSettings().lenient());
        when(snykAgent.description()).thenReturn("Analyze code diffs, then generate a comprehensive test suite covering changed behaviors.");
        when(snykAgent.model()).thenReturn("claude-4.5-sonnet");
        when(snykAgent.executionStrategy()).thenReturn("act");
        
        AgentCommand jiraAgent = mock(AgentCommand.class, withSettings().lenient());
        when(jiraAgent.description()).thenReturn("Read the Jira Bug ticket, Analyze it and ensure it is enough to move forward with.");
        when(jiraAgent.model()).thenReturn("claude-4.5-sonnet");
        when(jiraAgent.executionStrategy()).thenReturn("act");
        
        AgentCommand bugCodingAgent = mock(AgentCommand.class, withSettings().lenient());
        when(bugCodingAgent.description()).thenReturn("Retrieve the completed bug from Jira that is ready for implementation and checkout the code from the repository.");
        when(bugCodingAgent.model()).thenReturn("claude-4.5-sonnet");
        when(bugCodingAgent.executionStrategy()).thenReturn("act");
        
        commands.put("snyk_agent", snykAgent);
        commands.put("jira_agent", jiraAgent);
        commands.put("coding_agent", bugCodingAgent);
        
        return new AgentConfig(
            "1.0",
            "You are a Distinguished Software Engineer performing software development tasks.",
            commands
        );
    }
}
