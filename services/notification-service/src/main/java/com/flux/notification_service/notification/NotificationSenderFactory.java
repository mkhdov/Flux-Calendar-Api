package com.flux.notification_service.notification;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.flux.notification_service.reminder.ReminderType;

import lombok.extern.slf4j.Slf4j;

/**
 * Factory that resolves the correct NotificationSender based on ReminderType.
 * Uses Spring's auto-wiring to collect all NotificationSender implementations.
 */
@Component
@Slf4j
public class NotificationSenderFactory {

    private final Map<ReminderType, NotificationSender> senderMap;

    public NotificationSenderFactory(List<NotificationSender> senders) {
        this.senderMap = Map.of(
                ReminderType.EMAIL, findSender(senders, EmailNotificationSender.class),
                ReminderType.PUSH, findSender(senders, PushNotificationSender.class)
        );

        log.info("Initialized NotificationSenderFactory with {} sender(s): {}",
                senderMap.size(),
                senderMap.keySet().stream().map(Enum::name).collect(Collectors.joining(", ")));
    }

    public NotificationSender getSender(ReminderType type) {
        NotificationSender sender = senderMap.get(type);
        if (sender == null) {
            throw new UnsupportedOperationException("No sender registered for type: " + type);
        }
        return sender;
    }

    private <T extends NotificationSender> T findSender(List<NotificationSender> senders, Class<T> clazz) {
        return senders.stream()
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No sender found for: " + clazz.getSimpleName()));
    }
}
