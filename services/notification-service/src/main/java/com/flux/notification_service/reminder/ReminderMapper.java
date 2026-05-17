package com.flux.notification_service.reminder;

import org.springframework.stereotype.Component;

import com.flux.notification_service.reminder.dto.ReminderRequest;
import com.flux.notification_service.reminder.dto.ReminderResponse;

@Component
public class ReminderMapper {

    public Reminder toEntity(ReminderRequest request) {
        return Reminder.builder()
                .eventId(request.eventId())
                .userId(request.userId())
                .triggerTime(request.triggerTime())
                .type(request.type())
                .status(ReminderStatus.PENDING)
                .retryCount(0)
                .build();
    }

    public ReminderResponse toResponse(Reminder reminder) {
        return new ReminderResponse(
                reminder.getId(),
                reminder.getEventId(),
                reminder.getUserId(),
                reminder.getTriggerTime(),
                reminder.getType(),
                reminder.getStatus(),
                reminder.getRetryCount(),
                reminder.getEventStartTime(),
                reminder.getEventEndTime(),
                reminder.getCreatedAt(),
                reminder.getUpdatedAt()
        );
    }
}
