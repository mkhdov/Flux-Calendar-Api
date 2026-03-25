package com.flux.notification_service.notification;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Stub implementation for Push (Firebase Cloud Messaging) notifications.
 * Replace with actual FCM integration when Firebase credentials are available.
 */
@Component("pushNotificationSender")
@Slf4j
public class PushNotificationSender implements NotificationSender {

    @Override
    public void send(NotificationPayload payload) throws Exception {
        // TODO: Integrate with Firebase Cloud Messaging
        log.info("[PUSH STUB] Push notification would be sent to user {} for event {} (reminder {})",
                payload.getUserId(), payload.getEventId(), payload.getReminderId());

        log.debug("[PUSH STUB] Event: '{}', Trigger: {}",
                payload.getEventTitle(), payload.getTriggerTime());
    }
}
