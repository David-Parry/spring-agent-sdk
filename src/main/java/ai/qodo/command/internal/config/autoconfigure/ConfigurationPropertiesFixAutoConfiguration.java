/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.config.autoconfigure;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Auto-configuration to fix duplicate ConfigurationProperties beans in Spring Boot 3.x.
 * 
 * Spring Boot 3.x automatically creates beans for @ConfigurationProperties classes
 * with names based on the prefix (e.g., "messaging-ai.qodo.command.internal.config.MessagingProperties").
 * This conflicts with beans explicitly registered via @EnableConfigurationProperties.
 * 
 * This auto-configuration marks all explicitly registered @ConfigurationProperties beans
 * from the internal-core library as @Primary, ensuring they take precedence over the
 * automatically created prefix-based beans.
 */
@AutoConfiguration
public class ConfigurationPropertiesFixAutoConfiguration {
    
    @Bean
    public static BeanDefinitionRegistryPostProcessor configurationPropertiesFixer() {
        return new ConfigurationPropertiesFixer();
    }
    
    static class ConfigurationPropertiesFixer implements BeanDefinitionRegistryPostProcessor, Ordered {
        
        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            // Not used - we handle this in postProcessBeanFactory
        }
        
        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            // Mark all explicitly registered beans from internal-core config package as primary
            // to resolve conflicts with Spring Boot 3.x's prefix-based bean creation
            String[] beanNames = beanFactory.getBeanDefinitionNames();
            
            for (String beanName : beanNames) {
                try {
                    BeanDefinition bd = beanFactory.getBeanDefinition(beanName);
                    String beanClassName = bd.getBeanClassName();
                    
                    // Mark beans from internal-core config package as primary
                    // These are the explicitly registered @ConfigurationProperties beans
                    if (beanClassName != null && 
                        beanClassName.startsWith("ai.qodo.command.internal.config.") &&
                        !beanName.contains("-")) {  // Exclude prefix-based bean names
                        bd.setPrimary(true);
                    }
                } catch (Exception e) {
                    // Ignore - bean might not have a class name or other issues
                }
            }
        }
        
        @Override
        public int getOrder() {
            // Run after ConfigurationPropertiesBindingPostProcessor
            return Ordered.LOWEST_PRECEDENCE;
        }
    }
}
