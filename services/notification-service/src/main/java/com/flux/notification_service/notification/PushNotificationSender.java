package com.flux.notification_service.notification;

import com.flux.notification_service.device.DeviceTokenService;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("pushNotificationSender")
@RequiredArgsConstructor
@Slf4j
public class PushNotificationSender implements NotificationSender {

    private final DeviceTokenService deviceTokenService;

    @Override
    public void send(NotificationPayload payload) throws Exception {
        List<String> tokens = deviceTokenService.getTokensForUser(payload.getUserId());

        if (tokens.isEmpty()) {
            log.warn("[FCM] No device tokens found for user {}. Skipping push.", payload.getUserId());
            return;
        }

        String title = "📅 " + (payload.getEventTitle() != null ? payload.getEventTitle() : "Upcoming Event");
        String body  = buildBody(payload);

        for (String token : tokens) {
            try {
                Message message = Message.builder()
                        .setToken(token)
                        // Pure Data payload ensures the Flutter onBackgroundMessage handler 
                        // is aggressively awoken by the OS, rather than the OS rendering 
                        // it silently or incorrectly.
                        .putData("title", title)
                        .putData("body", body)
                        .putData("eventId",   nvl(payload.getEventId()))
                        .putData("reminderId", nvl(payload.getReminderId()))
                        .putData("type",      "REMINDER")
                        .build();

                String messageId = FirebaseMessaging.getInstance().send(message);
                log.info("[FCM] Push sent to user {} (token ...{}) — messageId: {}",
                        payload.getUserId(), token.substring(Math.max(0, token.length() - 6)), messageId);

            } catch (FirebaseMessagingException e) {
                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                        || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                    log.warn("[FCM] Stale token for user {}, skipping: {}", payload.getUserId(), e.getMessage());
                } else {
                    log.error("[FCM] Failed to send push to user {}: {}", payload.getUserId(), e.getMessage());
                    throw e;
                }
            }
        }
    }

    private String buildBody(NotificationPayload payload) {
        String title = payload.getEventTitle() != null ? payload.getEventTitle() : "your event";
        if (payload.getEventStartTime() != null) {
            return "Your event \"" + title + "\" is starting soon. Tap to view details.";
        }
        return "You have an upcoming event: " + title;
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }
}
