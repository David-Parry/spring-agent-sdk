/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auto-configuration for Qodo Command SDK Internal Core.
 * This class enables automatic discovery of all internal-core components
 * when the library is included as a dependency in a Spring Boot application.
 */
@AutoConfiguration
@ComponentScan(basePackages = "ai.qodo.command.internal")
@EnableConfigurationProperties({
    QodoProperties.class,
    MessagingProperties.class,
    AgentLoaderConfig.class,
    BlockedToolsConfiguration.class
})
public class QodoCommandAutoConfiguration {
    // Auto-configuration entry point - component scanning handles bean registration
}
