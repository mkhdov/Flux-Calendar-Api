package com.flux.calendar_service.kafka;

import com.flux.calendar_service.user.UserSync;
import com.flux.calendar_service.user.UserSyncRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserConsumer {

    private final UserSyncRepository userSyncRepository;

    @KafkaListener(topics = "user-topic", groupId = "calendar-group")
    public void consumeUserEvent(UserEvent event) {
        log.info("Received user event from Kafka: {}", event);

        if ("DELETED".equals(event.getType())) {
            userSyncRepository.deleteById(event.getUserId());
            log.info("Deleted user sync data for ID: {}", event.getUserId());
        } else {
            UserSync userSync = UserSync.builder()
                    .id(event.getUserId())
                    .email(event.getEmail())
                    .firstname(event.getFirstname())
                    .lastname(event.getLastname())
                    .build();
            userSyncRepository.save(userSync);
            log.info("Saved/Updated user sync data for ID: {}", event.getUserId());
        }
    }
}
