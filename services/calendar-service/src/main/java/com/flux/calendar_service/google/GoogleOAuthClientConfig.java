package com.flux.calendar_service.google;

import java.security.GeneralSecurityException;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "google.calendar.enabled", havingValue = "true", matchIfMissing = false)
public class GoogleOAuthClientConfig {

    @Bean
    public HttpTransport googleHttpTransport() throws GeneralSecurityException, java.io.IOException {
        return GoogleNetHttpTransport.newTrustedTransport();
    }
}
