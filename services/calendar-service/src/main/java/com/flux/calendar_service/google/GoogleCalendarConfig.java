package com.flux.calendar_service.google;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;


@Configuration
@ConditionalOnProperty(name = "google.calendar.enabled", havingValue = "true", matchIfMissing = false)
public class GoogleCalendarConfig {
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    @Value("${google.calendar.application-name:Flux Calendar Sync}")
    private String applicationName;

    @Value("${google.calendar.system-user-id:#{null}}")
    private String systemUserId;

    private final GoogleTokenService googleTokenService;

    public GoogleCalendarConfig(GoogleTokenService googleTokenService) {
        this.googleTokenService = googleTokenService;
    }

    @Bean
    public Calendar googleCalendar() throws Exception {
        if (systemUserId == null || systemUserId.isBlank()) {
            System.out.println("ℹ️ No system Google user configured. Calendar bean will be null.");
            return null;
        }
        Credential credential = googleTokenService.getValidCredential(systemUserId);

        if (credential == null) {
            return null;
        }

        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        return new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(applicationName)
                .build();
    }
}
