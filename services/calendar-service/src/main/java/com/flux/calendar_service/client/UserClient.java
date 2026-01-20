package com.flux.calendar_service.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserClient {

    private final com.flux.calendar_service.user.UserSyncRepository userSyncRepository;
    private final org.springframework.web.client.RestTemplate restTemplate;

    @org.springframework.beans.factory.annotation.Value("${user-management-service.url:http://localhost:8081}")
    private String userServiceUrl;

    public String getUserEmail(String userId) {
        // 1. Try local sync data first (Fast)
        var localUser = userSyncRepository.findById(userId);
        if (localUser.isPresent()) {
            return localUser.get().getEmail();
        }

        // 2. Fallback to REST call if Kafka sync hasn't happened yet (Reliable)
        log.info("User email not found in local sync for userId: {}. Falling back to REST call.", userId);
        try {
            com.flux.calendar_service.client.dto.UserResponse response = restTemplate.getForObject(
                    userServiceUrl + "/users/" + userId, com.flux.calendar_service.client.dto.UserResponse.class);

            if (response != null) {
                // Save to local sync for next time
                userSyncRepository.save(com.flux.calendar_service.user.UserSync.builder()
                        .id(userId)
                        .email(response.email())
                        .firstname(response.firstname())
                        .lastname(response.lastname())
                        .build());
                return response.email();
            }
        } catch (Exception e) {
            log.error("Failed to fetch user email via REST fallback for userId: {}", userId, e);
        }

        return null;
    }
}
