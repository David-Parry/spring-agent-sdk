/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StringConstantsTest {
    
    @Test
    void testEventKeyValue() {
        assertEquals("eventKey", StringConstants.EVENT_KEY.getValue());
        assertEquals("eventKey", StringConstants.EVENT_KEY.toString());
    }
    
    @Test
    void testSessionIdValue() {
        assertEquals("sessionId", StringConstants.SESSION_ID.getValue());
        assertEquals("sessionId", StringConstants.SESSION_ID.toString());
    }
    
    @Test
    void testSuccessValue() {
        assertEquals("success", StringConstants.SUCCESS.getValue());
        assertEquals("success", StringConstants.SUCCESS.toString());
    }
    
    @Test
    void testTypeValue() {
        assertEquals("type", StringConstants.TYPE.getValue());
        assertEquals("type", StringConstants.TYPE.toString());
    }
    
    @Test
    void testUserHomeValue() {
        assertEquals("user.home", StringConstants.USER_HOME.getValue());
        assertEquals("user.home", StringConstants.USER_HOME.toString());
    }
    
    @Test
    void testGetValueAndToStringAreConsistent() {
        for (StringConstants constant : StringConstants.values()) {
            assertEquals(constant.getValue(), constant.toString(), 
                "getValue() and toString() should return the same value for " + constant.name());
        }
    }
    
    @Test
    void testNoTransformation() {
        // Verify that the string values are not transformed (no uppercase, lowercase, etc.)
        assertEquals("eventKey", StringConstants.EVENT_KEY.getValue(), "eventKey should maintain camelCase");
        assertEquals("sessionId", StringConstants.SESSION_ID.getValue(), "sessionId should maintain camelCase");
        assertEquals("user.home", StringConstants.USER_HOME.getValue(), "user.home should maintain dots");
    }
}
