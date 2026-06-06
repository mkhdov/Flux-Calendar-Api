package com.flux.calendar_service.kafka;

import com.flux.calendar_service.calendar.Calendar;
import com.flux.calendar_service.calendar.CalendarRepository;
import com.flux.calendar_service.connects.ConnectService;
import com.flux.calendar_service.user.UserDataCleanupService;
import com.flux.calendar_service.user.UserSync;
import com.flux.calendar_service.user.UserSyncRepository;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserConsumer {

    private static final String USER_CREATED_TYPE = "CREATED";
    private static final String USER_DELETED_TYPE = "DELETED";
    private static final String DEFAULT_CALENDAR_TITLE = "Default";
    private static final String DEFAULT_CALENDAR_COLOR = "#808080";
    private static final String DEFAULT_TIMEZONE = "UTC";

    private final UserSyncRepository userSyncRepository;
    private final CalendarRepository calendarRepository;
    private final ConnectService connectService;
    private final UserDataCleanupService userDataCleanupService;

    @KafkaListener(topics = "user-topic", groupId = "calendar-group")
    public void consumeUserEvent(UserEvent event) {
        log.info("Received user event from Kafka: {}", event);

        if (event.getUserId() == null || event.getUserId().isBlank()) {
            log.warn("Received user event with null/blank userId — skipping. Event: {}", event);
            return;
        }

        if (USER_DELETED_TYPE.equals(event.getType())) {
            userDataCleanupService.cleanupDeletedUser(event.getUserId());
            return;
        }

        if (USER_CREATED_TYPE.equals(event.getType())) {
            createDefaultCalendarIfMissing(event.getUserId());
            connectService.create(event.getUserId());
        }

        UserSync userSync = UserSync.builder()
                .id(event.getUserId())
                .email(event.getEmail())
                .firstname(event.getFirstname())
                .lastname(event.getLastname())
                .build();
        userSyncRepository.save(userSync);
        log.info("Saved/Updated user sync data for ID: {}", event.getUserId());
    }

    private void createDefaultCalendarIfMissing(String userId) {
        if (userId == null || userId.isBlank()) {
            log.warn("Skipping default calendar creation because user id is empty");
            return;
        }

        if (calendarRepository.existsByUserIdAndTitleAndIsDeletedFalse(userId, DEFAULT_CALENDAR_TITLE)) {
            return;
        }

        boolean isPrimary = calendarRepository.findCalendarByUserIdAndIsDeletedFalse(userId).isEmpty();

        Calendar defaultCalendar = Calendar.builder()
                .userId(userId)
                .title(DEFAULT_CALENDAR_TITLE)
                .description("Default calendar")
                .colorHex(DEFAULT_CALENDAR_COLOR)
                .timezone(DEFAULT_TIMEZONE)
                .isPrimary(isPrimary)
                .isDeleted(false)
                .build();

        calendarRepository.save(defaultCalendar);
        log.info("Created default calendar for user ID: {}", userId);
    }

}
