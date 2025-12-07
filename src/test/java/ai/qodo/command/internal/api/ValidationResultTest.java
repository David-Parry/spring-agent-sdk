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

import java.util.ArrayList;
import java.util.List;

class ValidationResultTest {
    
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }
    
    @Test
    void testValidValidationResult() {
        ValidationResult result = new ValidationResult(true, new ArrayList<>());
        
        assertTrue(result.valid(), "ValidationResult should be valid");
        assertNotNull(result.errors(), "Errors list should not be null");
        assertTrue(result.errors().isEmpty(), "Errors list should be empty for valid result");
    }
    
    @Test
    void testInvalidValidationResultWithErrors() {
        List<ValidationError> errors = List.of(
            new ValidationError("field1", "Field is required"),
            new ValidationError("field2", "Invalid format")
        );
        
        ValidationResult result = new ValidationResult(false, errors);
        
        assertFalse(result.valid(), "ValidationResult should be invalid");
        assertNotNull(result.errors(), "Errors list should not be null");
        assertEquals(2, result.errors().size(), "Should have 2 errors");
        assertEquals("field1", result.errors().get(0).path());
        assertEquals("Field is required", result.errors().get(0).message());
    }
    
    @Test
    void testValidationResultEquality() {
        List<ValidationError> errors = List.of(
            new ValidationError("field1", "Error message")
        );
        
        ValidationResult result1 = new ValidationResult(false, errors);
        ValidationResult result2 = new ValidationResult(false, errors);
        
        assertEquals(result1, result2, "ValidationResults with same data should be equal");
        assertEquals(result1.hashCode(), result2.hashCode(), "Hash codes should match");
    }
    
    @Test
    void testJsonSerialization() throws Exception {
        List<ValidationError> errors = List.of(
            new ValidationError("username", "Username is required")
        );
        ValidationResult result = new ValidationResult(false, errors);
        
        String json = objectMapper.writeValueAsString(result);
        
        assertNotNull(json, "JSON should not be null");
        assertTrue(json.contains("\"valid\":false"), "JSON should contain valid field");
        assertTrue(json.contains("\"errors\""), "JSON should contain errors field");
        assertTrue(json.contains("username"), "JSON should contain error field name");
        assertTrue(json.contains("Username is required"), "JSON should contain error message");
    }
    
    @Test
    void testJsonDeserialization() throws Exception {
        String json = """
            {
                "valid": false,
                "errors": [
                    {"path": "email", "message": "Invalid email format"},
                    {"path": "password", "message": "Password too short"}
                ]
            }
            """;
        
        ValidationResult result = objectMapper.readValue(json, ValidationResult.class);
        
        assertNotNull(result, "ValidationResult should not be null");
        assertFalse(result.valid(), "Should be invalid");
        assertEquals(2, result.errors().size(), "Should have 2 errors");
        assertEquals("email", result.errors().get(0).path());
        assertEquals("Invalid email format", result.errors().get(0).message());
    }
    
    @Test
    void testValidationResultWithNullErrors() {
        ValidationResult result = new ValidationResult(true, null);
        
        assertTrue(result.valid(), "Should be valid");
        assertNull(result.errors(), "Errors can be null");
    }
    
    @Test
    void testValidationResultImmutability() {
        List<ValidationError> errors = new ArrayList<>();
        errors.add(new ValidationError("field1", "Error 1"));
        
        ValidationResult result = new ValidationResult(false, errors);
        
        // Try to modify the original list
        errors.add(new ValidationError("field2", "Error 2"));
        
        // Records don't create defensive copies by default, so the list reference is shared
        // This test verifies the behavior - in production, use List.copyOf() or Collections.unmodifiableList()
        assertEquals(2, result.errors().size(), "ValidationResult shares the list reference");
    }
    
    @Test
    void testValidationResultToString() {
        List<ValidationError> errors = List.of(
            new ValidationError("field1", "Error message")
        );
        ValidationResult result = new ValidationResult(false, errors);
        
        String stringRepresentation = result.toString();
        
        assertNotNull(stringRepresentation, "toString should not return null");
        assertTrue(stringRepresentation.contains("ValidationResult"), "toString should contain record name");
        assertTrue(stringRepresentation.contains("false"), "toString should contain valid status");
    }
}
