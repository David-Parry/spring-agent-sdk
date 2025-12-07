/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.api;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ToolResponseBuilderTest {
    
    private ToolResponseBuilder builder;
    
    @BeforeEach
    void setUp() {
        builder = new ToolResponseBuilder();
    }
    
    @Test
    void testBuilderCreation() {
        assertNotNull(builder, "Builder should not be null");
    }
    
    @Test
    void testBuildWithAllFields() {
        UserDataRequest userData = new UserDataRequest("1.0.0", "linux", "5.15", "vscode");
        Map<String, List<McpSchema.Tool>> tools = new HashMap<>();
        ToolAnswer answer = new ToolAnswer(false, List.of());
        
        ToolResponse response = builder
            .agentType("test-agent")
            .sessionId("session-123")
            .userData(userData)
            .gitSha1("git-sha-123")
            .tools(tools)
            .permissions("read-write")
            .projectsRootPath(List.of("/project/root"))
            .cwd("/current/dir")
            .tool("test-tool")
            .toolId("tool-id-456")
            .answer(answer)
            .build();
        
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
    void testFluentApiChaining() {
        ToolResponse response = builder
            .agentType("chain-agent")
            .sessionId("chain-session")
            .tool("chain-tool")
            .build();
        
        assertNotNull(response, "Fluent API should return valid ToolResponse");
        assertEquals("chain-agent", response.agentType());
        assertEquals("chain-session", response.sessionId());
        assertEquals("chain-tool", response.tool());
    }
    
    @Test
    void testUserDataInlineCreation() {
        ToolResponse response = builder
            .userData("1.0.0", "linux", "5.15", "vscode")
            .build();
        
        assertNotNull(response.userData(), "UserData should not be null");
        assertEquals("1.0.0", response.userData().extensionVersion());
        assertEquals("linux", response.userData().osPlatform());
        assertEquals("5.15", response.userData().osVersion());
        assertEquals("vscode", response.userData().editorType());
    }
    
    @Test
    void testAnswerInlineCreation() {
        List<ToolResponseContent> content = List.of(
            new ToolResponseContent("text", "Success")
        );
        
        ToolResponse response = builder
            .answer(false, content)
            .build();
        
        assertNotNull(response.answer(), "Answer should not be null");
        assertFalse(response.answer().isError());
        assertEquals(1, response.answer().content().size());
    }
    
    @Test
    void testAddProjectRootPath() {
        ToolResponse response = builder
            .addProjectRootPath("/path1")
            .addProjectRootPath("/path2")
            .addProjectRootPath("/path3")
            .build();
        
        assertNotNull(response.projectsRootPath(), "projectsRootPath should not be null");
        assertEquals(3, response.projectsRootPath().size(), "Should have 3 paths");
        assertTrue(response.projectsRootPath().contains("/path1"));
        assertTrue(response.projectsRootPath().contains("/path2"));
        assertTrue(response.projectsRootPath().contains("/path3"));
    }
    
    @Test
    void testAddProjectRootPathToExistingList() {
        ToolResponse response = builder
            .projectsRootPath(List.of("/existing1", "/existing2"))
            .addProjectRootPath("/new1")
            .build();
        
        assertNotNull(response.projectsRootPath(), "projectsRootPath should not be null");
        assertEquals(3, response.projectsRootPath().size(), "Should have 3 paths");
        assertTrue(response.projectsRootPath().contains("/existing1"));
        assertTrue(response.projectsRootPath().contains("/existing2"));
        assertTrue(response.projectsRootPath().contains("/new1"));
    }
    
    @Test
    void testBuildWithNoConfiguration() {
        ToolResponse response = builder.build();
        
        assertNotNull(response, "ToolResponse should be built even with no configuration");
        assertNull(response.agentType(), "Unconfigured fields should be null");
        assertNull(response.sessionId(), "Unconfigured fields should be null");
    }
    
    @Test
    void testToolsCollectionBuilder() {
        ToolResponseBuilder.ToolsCollectionBuilder toolsBuilder = ToolResponseBuilder.toolsCollection();
        
        ToolInputSchema schema = new ToolInputSchema("object", null, null);
        ToolDefinition tool1 = new ToolDefinition("tool1", "Description 1", schema);
        ToolDefinition tool2 = new ToolDefinition("tool2", "Description 2", schema);
        
        ToolsCollection tools = toolsBuilder
            .addTool(tool1)
            .addTool(tool2)
            .build();
        
        assertNotNull(tools, "ToolsCollection should not be null");
        assertEquals(2, tools.shell().size(), "Should have 2 tools");
    }
    
    @Test
    void testToolsCollectionBuilderWithInlineCreation() {
        ToolResponseBuilder.ToolsCollectionBuilder toolsBuilder = ToolResponseBuilder.toolsCollection();
        
        ToolInputSchema schema = new ToolInputSchema("object", null, null);
        ToolsCollection tools = toolsBuilder
            .addTool("tool1", "Description 1", schema)
            .addTool("tool2", "Description 2", schema)
            .build();
        
        assertNotNull(tools, "ToolsCollection should not be null");
        assertEquals(2, tools.shell().size(), "Should have 2 tools");
        assertEquals("tool1", tools.shell().get(0).name());
        assertEquals("Description 1", tools.shell().get(0).description());
    }
    
    @Test
    void testToolInputSchemaBuilder() {
        ToolResponseBuilder.ToolInputSchemaBuilder schemaBuilder = ToolResponseBuilder.inputSchema();
        
        ToolInputSchema schema = schemaBuilder
            .type("object")
            .addStringProperty("name")
            .addNumberProperty("age")
            .addBooleanProperty("active")
            .addRequired("name", "age")
            .build();
        
        assertNotNull(schema, "ToolInputSchema should not be null");
        assertEquals("object", schema.type());
        assertEquals(3, schema.properties().size(), "Should have 3 properties");
        assertEquals(2, schema.required().size(), "Should have 2 required fields");
        assertTrue(schema.required().contains("name"));
        assertTrue(schema.required().contains("age"));
    }
    
    @Test
    void testToolInputSchemaBuilderWithArrayProperty() {
        ToolResponseBuilder.ToolInputSchemaBuilder schemaBuilder = ToolResponseBuilder.inputSchema();
        
        ToolInputProperty itemType = new ToolInputProperty("string", null);
        ToolInputSchema schema = schemaBuilder
            .type("object")
            .addArrayProperty("tags", itemType)
            .build();
        
        assertNotNull(schema, "ToolInputSchema should not be null");
        assertTrue(schema.properties().containsKey("tags"));
        assertEquals("array", schema.properties().get("tags").type());
    }
    
    @Test
    void testToolInputSchemaBuilderWithStringArrayProperty() {
        ToolResponseBuilder.ToolInputSchemaBuilder schemaBuilder = ToolResponseBuilder.inputSchema();
        
        ToolInputSchema schema = schemaBuilder
            .type("object")
            .addStringArrayProperty("items")
            .build();
        
        assertNotNull(schema, "ToolInputSchema should not be null");
        assertTrue(schema.properties().containsKey("items"));
        assertEquals("array", schema.properties().get("items").type());
        assertEquals("string", schema.properties().get("items").items().type());
    }
    
    @Test
    void testToolAnswerBuilder() {
        ToolResponseBuilder.ToolAnswerBuilder answerBuilder = ToolResponseBuilder.answer();
        
        ToolResponseContent content1 = new ToolResponseContent("text", "Line 1");
        ToolResponseContent content2 = new ToolResponseContent("text", "Line 2");
        
        ToolAnswer answer = answerBuilder
            .isError(false)
            .addContent(content1)
            .addContent(content2)
            .build();
        
        assertNotNull(answer, "ToolAnswer should not be null");
        assertFalse(answer.isError());
        assertEquals(2, answer.content().size(), "Should have 2 content items");
    }
    
    @Test
    void testToolAnswerBuilderWithTextContent() {
        ToolResponseBuilder.ToolAnswerBuilder answerBuilder = ToolResponseBuilder.answer();
        
        ToolAnswer answer = answerBuilder
            .isError(false)
            .addTextContent("Success message")
            .addTextContent("Additional info")
            .build();
        
        assertNotNull(answer, "ToolAnswer should not be null");
        assertFalse(answer.isError());
        assertEquals(2, answer.content().size(), "Should have 2 content items");
        assertEquals("text", answer.content().get(0).type());
        assertEquals("Success message", answer.content().get(0).text());
    }
    
    @Test
    void testStaticFactoryMethods() {
        assertNotNull(ToolResponseBuilder.toolsCollection(), "toolsCollection factory should work");
        assertNotNull(ToolResponseBuilder.inputSchema(), "inputSchema factory should work");
        assertNotNull(ToolResponseBuilder.answer(), "answer factory should work");
    }
}
