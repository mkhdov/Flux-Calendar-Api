package com.flux.calendar_service.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

@Configuration
public class JacksonConfig {

    /**
     * Registers a custom module for flexible Instant handling.
     * This approach is highly portable across different Spring Boot versions.
     */
    @Bean
    public Module flexibleInstantModule() {
        SimpleModule module = new SimpleModule("FlexibleInstantModule");
        
        // Use our flexible parser for all incoming Instant fields
        module.addDeserializer(Instant.class, new FlexibleInstantDeserializer());
        
        // Use our standardized serializer for all outgoing Instant fields
        module.addSerializer(Instant.class, new FlexibleInstantSerializer());
        
        return module;
    }
}
