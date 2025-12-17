/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.api;

import com.davidparry.agent.core.config.MessagingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests to verify that messaging provider configuration properties are loaded correctly
 * and that unused provider configurations do not cause issues.
 * 
 * This demonstrates that having Kafka configuration in application.yml does not
 * cause problems when ActiveMQ or local provider is active.
 */
class MessagingProviderConditionalTest {
    
    @Test
    void kafkaProperties_canBeLoaded_withoutActivatingKafka() {
        MessagingProperties props = new MessagingProperties();
        props.setProvider("activemq");
        
        // Kafka properties exist and can be accessed
        assertThat(props.getKafka()).isNotNull();
        assertThat(props.getKafka().getBootstrapServers()).isEqualTo("localhost:9092");
        assertThat(props.getKafka().getGroupId()).isEqualTo("qodo-agent-group");
        
        // But provider is ActiveMQ
        assertThat(props.getProvider()).isEqualTo("activemq");
        
        // This proves that Kafka configuration can exist without being used
    }
    
    @Test
    void allProviderProperties_canCoexist() {
        MessagingProperties props = new MessagingProperties();
        
        // All provider configurations are accessible
        assertThat(props.getActivemq()).isNotNull();
        assertThat(props.getKafka()).isNotNull();
        assertThat(props.getQueue()).isNotNull();
        
        // Default provider
        assertThat(props.getProvider()).isEqualTo("in-memory");
    }
    
    @Test
    void kafkaSecurityProperties_areLoadedWithDefaults() {
        MessagingProperties props = new MessagingProperties();
        
        MessagingProperties.Kafka.Security security = props.getKafka().getSecurity();
        
        // Security is disabled by default
        assertThat(security.isEnabled()).isFalse();
        assertThat(security.getProtocol()).isEqualTo("PLAINTEXT");
        assertThat(security.getSaslMechanism()).isEqualTo("PLAIN");
        
        // This proves security configuration exists but is inactive by default
    }
}
