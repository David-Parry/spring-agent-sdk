/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.api;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolResponseBuilder {
    private String agentType;
    private String sessionId;
    private UserDataRequest userData;
    private String gitSha1;
    private Map<String, List<McpSchema.Tool>> tools;
    private String permissions;
    private List<String> projectsRootPath;
    private String cwd;
    private String tool;
    private String toolId;
    private ToolAnswer answer;

    public ToolResponseBuilder agentType(String agentType) {
        this.agentType = agentType;
        return this;
    }

    public ToolResponseBuilder sessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public ToolResponseBuilder userData(UserDataRequest userData) {
        this.userData = userData;
        return this;
    }

    public ToolResponseBuilder userData(String extensionVersion, String osPlatform, String osVersion, String editorType) {
        this.userData = new UserDataRequest(extensionVersion, osPlatform, osVersion, editorType);
        return this;
    }

    public ToolResponseBuilder gitSha1(String gitSha1) {
        this.gitSha1 = gitSha1;
        return this;
    }

    public ToolResponseBuilder tools(Map<String,List<McpSchema.Tool>> tools) {
        this.tools = tools;
        return this;
    }

    public ToolResponseBuilder permissions(String permissions) {
        this.permissions = permissions;
        return this;
    }

    public ToolResponseBuilder projectsRootPath(List<String> projectsRootPath) {
        this.projectsRootPath = new ArrayList<>(projectsRootPath);
        return this;
    }

    public ToolResponseBuilder addProjectRootPath(String path) {
        if(this.projectsRootPath == null){
            this.projectsRootPath = new ArrayList<>();
        }
        this.projectsRootPath.add(path);
        return this;
    }

    public ToolResponseBuilder cwd(String cwd) {
        this.cwd = cwd;
        return this;
    }

    public ToolResponseBuilder tool(String tool) {
        this.tool = tool;
        return this;
    }

    public ToolResponseBuilder toolId(String toolId) {
        this.toolId = toolId;
        return this;
    }

    public ToolResponseBuilder answer(ToolAnswer answer) {
        this.answer = answer;
        return this;
    }

    public ToolResponseBuilder answer(boolean isError, List<ToolResponseContent> content) {
        this.answer = new ToolAnswer(isError, content);
        return this;
    }

    public ToolResponse build() {
        return new ToolResponse(
            agentType,
            sessionId,
            userData,
            gitSha1,
            tools,
            permissions,
            projectsRootPath,
            cwd,
            tool,
            toolId,
            answer
        );
    }

    // Convenience methods for building complex nested structures
    public static class ToolsCollectionBuilder {
        private final List<ToolDefinition> tools = new ArrayList<>();

        public ToolsCollectionBuilder addTool(ToolDefinition toolDefinition) {
            this.tools.add(toolDefinition);
            return this;
        }

        public ToolsCollectionBuilder addTool(String name, String description, ToolInputSchema inputSchema) {
            this.tools.add(new ToolDefinition(name, description, inputSchema));
            return this;
        }

        public ToolsCollection build() {
            return new ToolsCollection(tools);
        }
    }

    public static class ToolInputSchemaBuilder {
        private String type;
        private Map<String, ToolInputProperty> properties = new HashMap<>();
        private List<String> required = new ArrayList<>();

        public ToolInputSchemaBuilder type(String type) {
            this.type = type;
            return this;
        }

        public ToolInputSchemaBuilder addProperty(String name, ToolInputProperty property) {
            this.properties.put(name, property);
            return this;
        }

        public ToolInputSchemaBuilder addStringProperty(String name) {
            this.properties.put(name, new ToolInputProperty("string", null));
            return this;
        }

        public ToolInputSchemaBuilder addNumberProperty(String name) {
            this.properties.put(name, new ToolInputProperty("number", null));
            return this;
        }

        public ToolInputSchemaBuilder addBooleanProperty(String name) {
            this.properties.put(name, new ToolInputProperty("boolean", null));
            return this;
        }

        public ToolInputSchemaBuilder addArrayProperty(String name, ToolInputProperty itemType) {
            this.properties.put(name, new ToolInputProperty("array", itemType));
            return this;
        }

        public ToolInputSchemaBuilder addStringArrayProperty(String name) {
            this.properties.put(name, new ToolInputProperty("array", new ToolInputProperty("string", null)));
            return this;
        }

        public ToolInputSchemaBuilder addRequired(String... fieldNames) {
            for (String fieldName : fieldNames) {
                this.required.add(fieldName);
            }
            return this;
        }

        public ToolInputSchema build() {
            return new ToolInputSchema(type, properties, required);
        }
    }

    public static class ToolAnswerBuilder {
        private boolean isError;
        private List<ToolResponseContent> content = new ArrayList<>();

        public ToolAnswerBuilder isError(boolean isError) {
            this.isError = isError;
            return this;
        }

        public ToolAnswerBuilder addContent(ToolResponseContent content) {
            this.content.add(content);
            return this;
        }

        public ToolAnswerBuilder addTextContent(String text) {
            this.content.add(new ToolResponseContent("text", text));
            return this;
        }

        public ToolAnswer build() {
            return new ToolAnswer(isError, content);
        }
    }

    // Static factory methods for convenience
    public static ToolsCollectionBuilder toolsCollection() {
        return new ToolsCollectionBuilder();
    }

    public static ToolInputSchemaBuilder inputSchema() {
        return new ToolInputSchemaBuilder();
    }

    public static ToolAnswerBuilder answer() {
        return new ToolAnswerBuilder();
    }
}