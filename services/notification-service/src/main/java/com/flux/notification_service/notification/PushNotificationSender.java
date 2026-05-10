package com.flux.notification_service.notification;

import com.flux.notification_service.device.DeviceTokenService;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component("pushNotificationSender")
@RequiredArgsConstructor
@Slf4j
public class PushNotificationSender implements NotificationSender {

    private final DeviceTokenService deviceTokenService;
    private static final DateTimeFormatter UTC_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    @Override
    public void send(NotificationPayload payload) throws Exception {
        List<String> tokens = deviceTokenService.getTokensForUser(payload.getUserId());

        if (tokens.isEmpty()) {
            log.warn("[FCM] No device tokens found for user {}. Skipping push.", payload.getUserId());
            return;
        }

        String title = buildTitle(payload);
        String body = buildBody(payload);
        Map<String, String> data = buildData(payload, title, body);

        for (String token : tokens) {
            try {
                Message message = Message.builder()
                        .setToken(token)
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .setNotification(AndroidNotification.builder()
                                        .setTitle(title)
                                        .setBody(body)
                                        .build())
                                .build())
                        .putAllData(data)
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

    private String buildTitle(NotificationPayload payload) {
        String eventTitle = clean(payload.getEventTitle());
        if (eventTitle == null) {
            return "📅 Upcoming Event";
        }
        return "📅 " + eventTitle;
    }

    private String buildBody(NotificationPayload payload) {
        String title = clean(payload.getEventTitle()) != null
                ? clean(payload.getEventTitle())
                : "Calendar Event";

        String when;
        if (Boolean.TRUE.equals(payload.getAllDay())) {
            when = "All-day";
        } else if (payload.getEventStartTime() != null) {
            when = "Starts " + UTC_FMT.format(payload.getEventStartTime());
        } else {
            when = "Starting soon";
        }

        String location = clean(payload.getEventLocation());
        String where = location != null
                ? " • " + location
                : "";

        return title + " — " + when + where;
    }

    private String clean(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }

    private Map<String, String> buildData(NotificationPayload payload, String title, String body) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "REMINDER");
        data.put("title", title);
        data.put("body", body);

        data.put("eventId", nvl(payload.getEventId()));
        data.put("reminderId", nvl(payload.getReminderId()));

        data.put("eventTitle", nvl(payload.getEventTitle()));
        data.put("eventLocation", nvl(payload.getEventLocation()));
        data.put("allDay", payload.getAllDay() != null ? payload.getAllDay().toString() : "");

        Instant start = payload.getEventStartTime();
        Instant end = payload.getEventEndTime();
        data.put("eventStartTime", start != null ? start.toString() : "");
        data.put("eventEndTime", end != null ? end.toString() : "");

        data.put("meetingUrl", nvl(payload.getMeetingUrl()));
        return data;
    }
}
