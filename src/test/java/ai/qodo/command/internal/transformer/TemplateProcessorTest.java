/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.transformer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for TemplateProcessor functionality
 */
public class TemplateProcessorTest {
    
    private TemplateProcessor processor;
    
    @BeforeEach
    void setUp() {
        processor = new TemplateProcessor(new ObjectMapper());
    }
    
    @Test
    void testSimpleJsonPointerReplacement() throws Exception {
        String template = "Hello {/name}, you are {/age} years old!";
        String json = "{\"name\": \"John\", \"age\": 25}";
        
        String result = processor.processTemplate(template, json);
        
        assertEquals("Hello John, you are 25 years old!", result);
    }
    
    @Test
    void testNestedJsonPointerReplacement() throws Exception {
        String template = "User: {/user/name}, City: {/user/address/city}";
        String json = """
            {
                "user": {
                    "name": "Alice",
                    "address": {
                        "city": "New York",
                        "country": "USA"
                    }
                }
            }
            """;
        
        String result = processor.processTemplate(template, json);
        
        assertEquals("User: Alice, City: New York", result);
    }
    
    @Test
    void testDotNotationReplacement() throws Exception {
        String template = "Product: {product.name}, Price: ${product.price}";
        String json = """
            {
                "product": {
                    "name": "Laptop",
                    "price": 999.99
                }
            }
            """;
        
        String result = processor.processTemplate(template, json);
        
        assertEquals("Product: Laptop, Price: $999.99", result);
    }
    
    @Test
    void testArrayAccess() throws Exception {
        String template = "First item: {/items/0/name}, Second item: {/items/1/name}";
        String json = """
            {
                "items": [
                    {"name": "Item A"},
                    {"name": "Item B"}
                ]
            }
            """;
        
        String result = processor.processTemplate(template, json);
        
        assertEquals("First item: Item A, Second item: Item B", result);
    }
    
    @Test
    void testMissingValues() throws Exception {
        String template = "Name: {/name}, Missing: {/missing}";
        String json = "{\"name\": \"John\"}";
        
        String result = processor.processTemplate(template, json);
        
        assertEquals("Name: John, Missing: {/missing}", result);
    }
    
    @Test
    void testMixedNotations() throws Exception {
        String template = "JSON Pointer: {/user/name}, Dot notation: {user.email}";
        String json = """
            {
                "user": {
                    "name": "Bob",
                    "email": "bob@example.com"
                }
            }
            """;
        
        String result = processor.processTemplate(template, json);
        
        assertEquals("JSON Pointer: Bob, Dot notation: bob@example.com", result);
    }
    
    @Test
    void testComplexTemplate() throws Exception {
        String template = """
            Event: {/eventType}
            Timestamp: {/timestamp}
            Project: {/project/name} (ID: {/project/id})
            Issues: {/newIssues/0/title} - Severity: {/newIssues/0/severity}
            """;
        
        String json = """
            {
                "eventType": "security_alert",
                "timestamp": "2023-01-01T00:00:00Z",
                "project": {
                    "id": "proj-123",
                    "name": "My Project"
                },
                "newIssues": [
                    {
                        "title": "SQL Injection",
                        "severity": "high"
                    }
                ]
            }
            """;
        
        String result = processor.processTemplate(template, json);
        
        String expected = """
            Event: security_alert
            Timestamp: 2023-01-01T00:00:00Z
            Project: My Project (ID: proj-123)
            Issues: SQL Injection - Severity: high
            """;
        
        assertEquals(expected, result);
    }
    
    @Test
    void testEmptyTemplate() throws Exception {
        String template = "";
        String json = "{\"name\": \"John\"}";
        
        String result = processor.processTemplate(template, json);
        
        assertEquals("", result);
    }
    
    @Test
    void testTemplateWithoutMarkers() throws Exception {
        String template = "This is a plain text template without any markers.";
        String json = "{\"name\": \"John\"}";
        
        String result = processor.processTemplate(template, json);
        
        assertEquals("This is a plain text template without any markers.", result);
    }
    
    // ========================================
    // Tests for Non-Matching Key Behavior
    // These tests prove that non-matching keys preserve the original placeholder
    // rather than being replaced with error messages or blank strings
    // ========================================
    
    @Test
    void testNonMatchingJsonPointerPreservesOriginal() throws Exception {
        String template = "Value: {/nonexistent/path}";
        String json = "{\"existing\": \"value\"}";
        
        String result = processor.processTemplate(template, json);
        
        // Proves the original placeholder is preserved unchanged
        assertEquals("Value: {/nonexistent/path}", result);
    }
    
    @Test
    void testNonMatchingDotNotationPreservesOriginal() throws Exception {
        String template = "User email: {user.email}";
        String json = "{\"user\": {\"name\": \"John\"}}";
        
        String result = processor.processTemplate(template, json);
        
        assertEquals("User email: {user.email}", result);
    }
    
    @Test
    void testMultipleNonMatchingKeysAllPreserved() throws Exception {
        String template = "A: {/missing1}, B: {/missing2}, C: {/missing3}";
        String json = "{\"existing\": \"value\"}";
        
        String result = processor.processTemplate(template, json);
        
        assertEquals("A: {/missing1}, B: {/missing2}, C: {/missing3}", result);
    }
    
    @Test
    void testMixOfMatchingAndNonMatchingKeys() throws Exception {
        String template = "Name: {/name}, Age: {/age}, Email: {/email}";
        String json = "{\"name\": \"Alice\", \"age\": 30}";
        
        String result = processor.processTemplate(template, json);
        
        // Name and age should be replaced with actual values
        // Email placeholder should be preserved unchanged
        assertEquals("Name: Alice, Age: 30, Email: {/email}", result);
    }
    
    @Test
    void testNullValuePreservesOriginal() throws Exception {
        String template = "Value: {/nullField}";
        String json = "{\"nullField\": null}";
        
        String result = processor.processTemplate(template, json);
        
        assertEquals("Value: {/nullField}", result);
    }
    
    @Test
    void testOriginalPlaceholderIsPreserved() throws Exception {
        String template = "Original: {/missing}";
        String json = "{}";
        
        String result = processor.processTemplate(template, json);
        
        // Explicitly assert the original IS preserved
        assertEquals("Original: {/missing}", result);
    }
    
    @Test
    void testNonMatchingKeyIsNotReplacedWithEmptyString() throws Exception {
        String template = "Value: {/missing}";
        String json = "{}";
        
        String result = processor.processTemplate(template, json);
        
        // Explicitly assert it's NOT replaced with empty string
        assertNotEquals("Value: ", result);
        // Verify the placeholder is preserved
        assertEquals("Value: {/missing}", result);
    }
    
    @Test
    void testDeeplyNestedNonMatchingPath() throws Exception {
        String template = "Value: {/level1/level2/level3/level4/missing}";
        String json = "{\"level1\": {\"level2\": {}}}";
        
        String result = processor.processTemplate(template, json);
        
        assertEquals("Value: {/level1/level2/level3/level4/missing}", result);
    }
    
    @Test
    void testArrayIndexOutOfBounds() throws Exception {
        String template = "Item: {/items/5/name}";
        String json = "{\"items\": [{\"name\": \"Item1\"}, {\"name\": \"Item2\"}]}";
        
        String result = processor.processTemplate(template, json);
        
        // Should preserve original placeholder for out-of-bounds index
        assertEquals("Item: {/items/5/name}", result);
    }
    
    @Test
    void testSpecialCharactersInNonMatchingKeys() throws Exception {
        String template = "Value: {/key-with-dashes/sub_key}";
        String json = "{}";
        
        String result = processor.processTemplate(template, json);
        
        assertEquals("Value: {/key-with-dashes/sub_key}", result);
    }
    
    @Test
    void testEmptyJsonObjectWithPlaceholders() throws Exception {
        String template = "User: {/user}, Role: {/role}";
        String json = "{}";
        
        String result = processor.processTemplate(template, json);
        
        assertEquals("User: {/user}, Role: {/role}", result);
    }
    
    @Test
    void testPartialPathMatch() throws Exception {
        // Test when part of the path exists but not the full path
        String template = "Value: {/user/profile/avatar}";
        String json = "{\"user\": {\"name\": \"John\"}}";
        
        String result = processor.processTemplate(template, json);
        
        assertEquals("Value: {/user/profile/avatar}", result);
    }
    
    @Test
    void testPlaceholderPreservationAllowsMultiplePassProcessing() throws Exception {
        // This test demonstrates that preserving placeholders allows for multi-pass processing
        String template = "First: {/first}, Second: {/second}";
        String json1 = "{\"first\": \"value1\"}";
        
        // First pass - only first is replaced
        String result1 = processor.processTemplate(template, json1);
        assertEquals("First: value1, Second: {/second}", result1);
        
        // Second pass - can now replace the second placeholder
        String json2 = "{\"second\": \"value2\"}";
        String result2 = processor.processTemplate(result1, json2);
        assertEquals("First: value1, Second: value2", result2);
    }
    
    @Test
    void testErrorInPathPreservesOriginal() throws Exception {
        // Test that errors during path traversal preserve the original placeholder
        String template = "Value: {/some/complex/path}";
        String json = "{\"some\": \"not-an-object\"}";
        
        String result = processor.processTemplate(template, json);
        
        // Should preserve placeholder when path traversal fails
        assertEquals("Value: {/some/complex/path}", result);
    }
}
