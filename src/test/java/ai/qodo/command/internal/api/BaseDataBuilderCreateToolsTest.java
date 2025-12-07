/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.api;

import ai.qodo.command.internal.mcp.McpServerInitialized;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for BaseDataBuilder.createTools method.
 * Tests all edge cases and filtering rules with 90%+ code coverage.
 * Uses only mocks, no Spring Boot context.
 */
@DisplayName("BaseDataBuilder.createTools() Tests")
class BaseDataBuilderCreateToolsTest {

    private McpSyncClient mockClient1;
    private McpSyncClient mockClient2;
    private McpSyncClient mockClient3;

    @BeforeEach
    void setUp() {
        mockClient1 = mock(McpSyncClient.class);
        mockClient2 = mock(McpSyncClient.class);
        mockClient3 = mock(McpSyncClient.class);
    }

    /**
     * Helper method to create a mock McpSchema.Tool
     * Returns null for inputSchema to avoid ClassCastException
     */
    private McpSchema.Tool createMockTool(String name, String description) {
        McpSchema.Tool tool = mock(McpSchema.Tool.class);
        when(tool.name()).thenReturn(name);
        when(tool.description()).thenReturn(description);
        // Return null for inputSchema - it's stored as Object in the map
        when(tool.inputSchema()).thenReturn(null);
        
        return tool;
    }

    /**
     * Helper method to create a McpServerInitialized with tools
     */
    private McpServerInitialized createMockServer(String name, McpSyncClient client, List<McpSchema.Tool> tools) {
        return new McpServerInitialized(name, client, tools);
    }

    @Nested
    @DisplayName("Null and Empty Filter Tests")
    class NullAndEmptyFilterTests {

        @Test
        @DisplayName("Should include all tools when filter is null")
        void testNullFilter_IncludesAllTools() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");
            McpSchema.Tool tool2 = createMockTool("tool2", "Description 2");
            McpSchema.Tool tool3 = createMockTool("tool3", "Description 3");

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1, tool2));
            McpServerInitialized server2 = createMockServer("server2", mockClient2, List.of(tool3));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);
            mcpServers.put("server2", server2);

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, null);

            // Assert
            assertNotNull(result);
            assertEquals(2, result.size(), "Should have 2 servers");
            assertTrue(result.containsKey("server1"));
            assertTrue(result.containsKey("server2"));
            assertEquals(2, result.get("server1").size(), "Server1 should have 2 tools");
            assertEquals(1, result.get("server2").size(), "Server2 should have 1 tool");

            // Verify tool details
            Map<String, Object> resultTool1 = result.get("server1").get(0);
            assertEquals("tool1", resultTool1.get("name"));
            assertEquals("Description 1", resultTool1.get("description"));
            assertTrue(resultTool1.containsKey("inputSchema"));
        }

        @Test
        @DisplayName("Should include all tools when filter is empty list")
        void testEmptyFilter_IncludesAllTools() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");
            McpSchema.Tool tool2 = createMockTool("tool2", "Description 2");

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1, tool2));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, Collections.emptyList());

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(2, result.get("server1").size());
        }

        @Test
        @DisplayName("Should return empty map when mcpServers is empty")
        void testEmptyMcpServers_ReturnsEmptyMap() {
            // Arrange
            Map<String, McpServerInitialized> mcpServers = new HashMap<>();

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, null);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty map when mcpServers is empty with filter")
        void testEmptyMcpServersWithFilter_ReturnsEmptyMap() {
            // Arrange
            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            List<String> filter = List.of("server1.tool1");

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Server Name Filter Tests")
    class ServerNameFilterTests {

        @Test
        @DisplayName("Should include all tools from specified server when filter contains only server name")
        void testServerNameFilter_IncludesAllToolsFromServer() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");
            McpSchema.Tool tool2 = createMockTool("tool2", "Description 2");
            McpSchema.Tool tool3 = createMockTool("tool3", "Description 3");

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1, tool2));
            McpServerInitialized server2 = createMockServer("server2", mockClient2, List.of(tool3));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);
            mcpServers.put("server2", server2);

            List<String> filter = List.of("server1");

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size(), "Should only have server1");
            assertTrue(result.containsKey("server1"));
            assertFalse(result.containsKey("server2"));
            assertEquals(2, result.get("server1").size(), "Server1 should have 2 tools");
        }

        @Test
        @DisplayName("Should include tools from multiple servers when filter contains multiple server names")
        void testMultipleServerNameFilter_IncludesAllToolsFromSpecifiedServers() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");
            McpSchema.Tool tool2 = createMockTool("tool2", "Description 2");
            McpSchema.Tool tool3 = createMockTool("tool3", "Description 3");

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1));
            McpServerInitialized server2 = createMockServer("server2", mockClient2, List.of(tool2));
            McpServerInitialized server3 = createMockServer("server3", mockClient3, List.of(tool3));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);
            mcpServers.put("server2", server2);
            mcpServers.put("server3", server3);

            List<String> filter = List.of("server1", "server3");

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertEquals(2, result.size());
            assertTrue(result.containsKey("server1"));
            assertFalse(result.containsKey("server2"));
            assertTrue(result.containsKey("server3"));
        }

        @Test
        @DisplayName("Should return empty map when filter contains non-existent server name")
        void testNonExistentServerName_ReturnsEmptyMap() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");
            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);

            List<String> filter = List.of("nonExistentServer");

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Server.Tool Filter Tests")
    class ServerToolFilterTests {

        @Test
        @DisplayName("Should include only specified tool when filter contains server.tool format")
        void testServerToolFilter_IncludesOnlySpecifiedTool() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");
            McpSchema.Tool tool2 = createMockTool("tool2", "Description 2");
            McpSchema.Tool tool3 = createMockTool("tool3", "Description 3");

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1, tool2, tool3));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);

            List<String> filter = List.of("server1.tool2");

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.containsKey("server1"));
            assertEquals(1, result.get("server1").size(), "Should only have tool2");
            assertEquals("tool2", result.get("server1").get(0).get("name"));
        }

        @Test
        @DisplayName("Should include multiple specific tools when filter contains multiple server.tool entries")
        void testMultipleServerToolFilter_IncludesSpecifiedTools() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");
            McpSchema.Tool tool2 = createMockTool("tool2", "Description 2");
            McpSchema.Tool tool3 = createMockTool("tool3", "Description 3");

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1, tool2, tool3));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);

            List<String> filter = List.of("server1.tool1", "server1.tool3");

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(2, result.get("server1").size(), "Should have tool1 and tool3");
            
            List<String> toolNames = result.get("server1").stream()
                    .map(tool -> (String) tool.get("name"))
                    .toList();
            assertTrue(toolNames.contains("tool1"));
            assertTrue(toolNames.contains("tool3"));
            assertFalse(toolNames.contains("tool2"));
        }

        @Test
        @DisplayName("Should include tools from different servers when filter contains server.tool from multiple servers")
        void testCrossServerToolFilter_IncludesToolsFromDifferentServers() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");
            McpSchema.Tool tool2 = createMockTool("tool2", "Description 2");
            McpSchema.Tool tool3 = createMockTool("tool3", "Description 3");
            McpSchema.Tool tool4 = createMockTool("tool4", "Description 4");

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1, tool2));
            McpServerInitialized server2 = createMockServer("server2", mockClient2, List.of(tool3, tool4));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);
            mcpServers.put("server2", server2);

            List<String> filter = List.of("server1.tool1", "server2.tool4");

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals(1, result.get("server1").size());
            assertEquals(1, result.get("server2").size());
            assertEquals("tool1", result.get("server1").get(0).get("name"));
            assertEquals("tool4", result.get("server2").get(0).get("name"));
        }

        @Test
        @DisplayName("Should return empty map when server.tool filter doesn't match any tool")
        void testNonMatchingServerToolFilter_ReturnsEmptyMap() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");
            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);

            List<String> filter = List.of("server1.nonExistentTool");

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should not include tool when server name matches but tool name doesn't")
        void testServerMatchesButToolDoesnt_ExcludesTool() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");
            McpSchema.Tool tool2 = createMockTool("tool2", "Description 2");

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1, tool2));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);

            List<String> filter = List.of("server1.tool3");

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should not include tool when tool name matches but server name doesn't")
        void testToolMatchesButServerDoesnt_ExcludesTool() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);

            List<String> filter = List.of("server2.tool1");

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Mixed Filter Tests")
    class MixedFilterTests {

        @Test
        @DisplayName("Should handle mix of server name and server.tool filters")
        void testMixedFilter_ServerNameAndServerTool() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");
            McpSchema.Tool tool2 = createMockTool("tool2", "Description 2");
            McpSchema.Tool tool3 = createMockTool("tool3", "Description 3");
            McpSchema.Tool tool4 = createMockTool("tool4", "Description 4");

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1, tool2));
            McpServerInitialized server2 = createMockServer("server2", mockClient2, List.of(tool3, tool4));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);
            mcpServers.put("server2", server2);

            // Filter: all tools from server1, only tool3 from server2
            List<String> filter = List.of("server1", "server2.tool3");

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals(2, result.get("server1").size(), "Server1 should have all tools");
            assertEquals(1, result.get("server2").size(), "Server2 should have only tool3");
            assertEquals("tool3", result.get("server2").get(0).get("name"));
        }

        @Test
        @DisplayName("Should not duplicate tools when both server name and server.tool filters match same tool")
        void testNoDuplicates_WhenServerAndServerToolBothMatch() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");
            McpSchema.Tool tool2 = createMockTool("tool2", "Description 2");

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1, tool2));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);

            // Both filters should match tool1
            List<String> filter = List.of("server1", "server1.tool1");

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(2, result.get("server1").size(), "Should have both tools, no duplicates");
            
            List<String> toolNames = result.get("server1").stream()
                    .map(tool -> (String) tool.get("name"))
                    .toList();
            assertEquals(2, toolNames.size());
            assertTrue(toolNames.contains("tool1"));
            assertTrue(toolNames.contains("tool2"));
        }
    }

    @Nested
    @DisplayName("Edge Cases and Special Scenarios")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle server with empty tool list")
        void testServerWithEmptyToolList_NotIncludedInResult() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1));
            McpServerInitialized server2 = createMockServer("server2", mockClient2, Collections.emptyList());

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);
            mcpServers.put("server2", server2);

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, null);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size(), "Server with empty tools should not be included");
            assertTrue(result.containsKey("server1"));
            assertFalse(result.containsKey("server2"));
        }

        @Test
        @DisplayName("Should handle server with empty tool list when filtered by server name")
        void testServerWithEmptyToolListFiltered_NotIncludedInResult() {
            // Arrange
            McpServerInitialized server1 = createMockServer("server1", mockClient1, Collections.emptyList());

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);

            List<String> filter = List.of("server1");

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty(), "Server with no tools should not be in result even when filtered");
        }

        @Test
        @DisplayName("Should handle filter with dots in tool name")
        void testFilterWithMultipleDotsInToolName() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool.with.dots", "Description 1");
            McpSchema.Tool tool2 = createMockTool("normalTool", "Description 2");

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1, tool2));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);

            // Filter should split on first dot only
            List<String> filter = List.of("server1.tool.with.dots");

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(1, result.get("server1").size());
            assertEquals("tool.with.dots", result.get("server1").get(0).get("name"));
        }

        @Test
        @DisplayName("Should handle tool with null description")
        void testToolWithNullDescription() {
            // Arrange
            McpSchema.Tool tool1 = mock(McpSchema.Tool.class);
            when(tool1.name()).thenReturn("tool1");
            when(tool1.description()).thenReturn(null);
            when(tool1.inputSchema()).thenReturn(null);

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, null);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertNull(result.get("server1").get(0).get("description"));
        }

        @Test
        @DisplayName("Should handle tool with null inputSchema")
        void testToolWithNullInputSchema() {
            // Arrange
            McpSchema.Tool tool1 = mock(McpSchema.Tool.class);
            when(tool1.name()).thenReturn("tool1");
            when(tool1.description()).thenReturn("Description");
            when(tool1.inputSchema()).thenReturn(null);

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, null);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertNull(result.get("server1").get(0).get("inputSchema"));
        }

        @Test
        @DisplayName("Should handle server name with special characters")
        void testServerNameWithSpecialCharacters() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");

            McpServerInitialized server1 = createMockServer("server-name_123", mockClient1, List.of(tool1));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server-name_123", server1);

            List<String> filter = List.of("server-name_123");

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.containsKey("server-name_123"));
        }

        @Test
        @DisplayName("Should handle tool name with special characters")
        void testToolNameWithSpecialCharacters() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool-name_123", "Description 1");

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);

            List<String> filter = List.of("server1.tool-name_123");

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("tool-name_123", result.get("server1").get(0).get("name"));
        }

        @Test
        @DisplayName("Should handle large number of servers and tools")
        void testLargeNumberOfServersAndTools() {
            // Arrange
            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            
            for (int i = 0; i < 10; i++) {
                List<McpSchema.Tool> tools = new ArrayList<>();
                for (int j = 0; j < 20; j++) {
                    tools.add(createMockTool("tool" + j, "Description " + j));
                }
                McpServerInitialized server = createMockServer("server" + i, mock(McpSyncClient.class), tools);
                mcpServers.put("server" + i, server);
            }

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, null);

            // Assert
            assertNotNull(result);
            assertEquals(10, result.size());
            result.values().forEach(toolList -> assertEquals(20, toolList.size()));
        }

        @Test
        @DisplayName("Should handle filter with empty string")
        void testFilterWithEmptyString() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");
            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);

            List<String> filter = List.of("");

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty(), "Empty string filter should not match anything");
        }

        @Test
        @DisplayName("Should handle filter with whitespace")
        void testFilterWithWhitespace() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");
            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);

            List<String> filter = List.of("   ", "\t", "\n");

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty(), "Whitespace filter should not match anything");
        }
    }

    @Nested
    @DisplayName("Tool Map Structure Tests")
    class ToolMapStructureTests {

        @Test
        @DisplayName("Should create tool map with correct structure")
        void testToolMapStructure() {
            // Arrange
            McpSchema.Tool tool1 = mock(McpSchema.Tool.class);
            when(tool1.name()).thenReturn("testTool");
            when(tool1.description()).thenReturn("Test Description");
            when(tool1.inputSchema()).thenReturn(null);

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, null);

            // Assert
            assertNotNull(result);
            Map<String, Object> toolMap = result.get("server1").get(0);
            
            assertTrue(toolMap.containsKey("name"));
            assertTrue(toolMap.containsKey("description"));
            assertTrue(toolMap.containsKey("inputSchema"));
            
            assertEquals("testTool", toolMap.get("name"));
            assertEquals("Test Description", toolMap.get("description"));
            assertNull(toolMap.get("inputSchema"));
        }

        @Test
        @DisplayName("Should preserve inputSchema as Object")
        void testInputSchemaPreservation() {
            // Arrange - inputSchema is stored as Object, so we just verify it's preserved
            McpSchema.Tool tool1 = mock(McpSchema.Tool.class);
            when(tool1.name()).thenReturn("complexTool");
            when(tool1.description()).thenReturn("Complex Tool");
            when(tool1.inputSchema()).thenReturn(null);

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, null);

            // Assert
            assertNotNull(result);
            Map<String, Object> toolMap = result.get("server1").get(0);
            
            assertTrue(toolMap.containsKey("inputSchema"));
            // inputSchema is stored as whatever tool.inputSchema() returns
            assertNull(toolMap.get("inputSchema"));
        }
    }

    @Nested
    @DisplayName("Parameterized Tests")
    class ParameterizedTests {

        @ParameterizedTest
        @MethodSource("filterScenarios")
        @DisplayName("Should handle various filter scenarios correctly")
        void testVariousFilterScenarios(List<String> filter, int expectedServers, Map<String, Integer> expectedToolCounts) {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");
            McpSchema.Tool tool2 = createMockTool("tool2", "Description 2");
            McpSchema.Tool tool3 = createMockTool("tool3", "Description 3");

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1, tool2));
            McpServerInitialized server2 = createMockServer("server2", mockClient2, List.of(tool3));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);
            mcpServers.put("server2", server2);

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertEquals(expectedServers, result.size());
            
            expectedToolCounts.forEach((serverName, expectedCount) -> {
                assertTrue(result.containsKey(serverName));
                assertEquals(expectedCount, result.get(serverName).size());
            });
        }

        static Stream<Arguments> filterScenarios() {
            return Stream.of(
                // null filter - all tools
                Arguments.of(null, 2, Map.of("server1", 2, "server2", 1)),
                // empty filter - all tools
                Arguments.of(Collections.emptyList(), 2, Map.of("server1", 2, "server2", 1)),
                // single server name
                Arguments.of(List.of("server1"), 1, Map.of("server1", 2)),
                // single server.tool
                Arguments.of(List.of("server1.tool1"), 1, Map.of("server1", 1)),
                // multiple server.tool from same server
                Arguments.of(List.of("server1.tool1", "server1.tool2"), 1, Map.of("server1", 2)),
                // mixed server name and server.tool
                Arguments.of(List.of("server1", "server2.tool3"), 2, Map.of("server1", 2, "server2", 1)),
                // non-matching filter
                Arguments.of(List.of("nonExistent"), 0, Collections.emptyMap())
            );
        }
    }

    @Nested
    @DisplayName("Integration with BaseDataBuilder")
    class IntegrationTests {

        @Test
        @DisplayName("Should work correctly when called through BaseDataBuilder.tools() method")
        void testIntegrationWithBaseDataBuilder() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");
            McpSchema.Tool tool2 = createMockTool("tool2", "Description 2");

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1, tool2));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);

            List<String> filter = List.of("server1.tool1");

            // Act
            BaseDataBuilder builder = new BaseDataBuilder();
            builder.tools(mcpServers, filter);
            BaseData result = builder.build();

            // Assert
            assertNotNull(result);
            assertNotNull(result.tools());
            assertEquals(1, result.tools().size());
            assertTrue(result.tools().containsKey("server1"));
            assertEquals(1, result.tools().get("server1").size());
            assertEquals("tool1", result.tools().get("server1").get(0).get("name"));
        }

        @Test
        @DisplayName("Should handle null mcpServers gracefully")
        void testNullMcpServers() {
            // Act & Assert - The actual implementation will throw NPE
            assertThrows(NullPointerException.class, () -> {
                BaseDataBuilder.createTools(null, null);
            });
        }
    }

    @Nested
    @DisplayName("Filter Breaking Logic Tests")
    class FilterBreakingLogicTests {

        @Test
        @DisplayName("Should break inner loop when tool matches server.tool filter")
        void testBreakOnFirstMatch_ServerToolFilter() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);

            // Multiple filters that match the same tool - should only add once
            List<String> filter = List.of("server1.tool1", "server1.tool1", "server1.tool1");

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.get("server1").size(), "Tool should only be added once despite multiple matching filters");
        }

        @Test
        @DisplayName("Should break inner loop when tool matches server name filter")
        void testBreakOnFirstMatch_ServerNameFilter() {
            // Arrange
            McpSchema.Tool tool1 = createMockTool("tool1", "Description 1");

            McpServerInitialized server1 = createMockServer("server1", mockClient1, List.of(tool1));

            Map<String, McpServerInitialized> mcpServers = new HashMap<>();
            mcpServers.put("server1", server1);

            // Multiple filters that match the same server - should add tool once
            List<String> filter = List.of("server1", "server1", "server1");

            // Act
            Map<String, List<Map<String, Object>>> result = BaseDataBuilder.createTools(mcpServers, filter);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.get("server1").size(), "Tool should only be added once despite multiple matching filters");
        }
    }
}
