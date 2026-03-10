package com.flux.calendar_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Instant;
import org.springframework.core.convert.converter.Converter;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new FlexibleInstantConverter());
    }

    private static class FlexibleInstantConverter implements Converter<String, Instant> {
        @Override
        public Instant convert(String source) {
            return FlexibleInstantParser.parse(source);
        }
    }
}
