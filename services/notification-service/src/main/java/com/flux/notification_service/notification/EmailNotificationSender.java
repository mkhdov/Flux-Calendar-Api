package com.flux.notification_service.notification;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component("emailNotificationSender")
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' hh:mm a 'UTC'")
                    .withZone(ZoneOffset.UTC);

    @Override
    public void send(NotificationPayload payload) throws Exception {
        String to = payload.getUserEmail();
        if (to == null || to.isBlank()) {
            log.warn("Cannot send email notification: userEmail is null for reminder {}", payload.getReminderId());
            throw new IllegalArgumentException("User email is required for email notifications");
        }

        String subject = "⏰ Reminder: " + payload.getEventTitle();
        String body = buildHtmlBody(payload);

        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(senderEmail, "Flux Calendar");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, true);

        javaMailSender.send(message);

        log.info("Email notification sent to {} for event {} (reminder {})",
                to, payload.getEventId(), payload.getReminderId());
    }

    private String buildHtmlBody(NotificationPayload payload) {
        String eventTime = payload.getEventStartTime() != null
                ? FORMATTER.format(payload.getEventStartTime())
                : "Not specified";

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <style>
                        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 0; background-color: #f4f4f9; }
                        .container { max-width: 600px; margin: 20px auto; background: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 2px 12px rgba(0,0,0,0.08); }
                        .header { background: linear-gradient(135deg, #6366f1, #8b5cf6); padding: 28px 32px; color: white; }
                        .header h1 { margin: 0; font-size: 22px; font-weight: 600; }
                        .body { padding: 28px 32px; color: #374151; }
                        .event-card { background: #f8f7ff; border-left: 4px solid #6366f1; padding: 16px 20px; border-radius: 8px; margin: 16px 0; }
                        .event-card h2 { margin: 0 0 8px; font-size: 18px; color: #1f2937; }
                        .event-card p { margin: 4px 0; font-size: 14px; color: #6b7280; }
                        .footer { padding: 16px 32px; text-align: center; font-size: 12px; color: #9ca3af; border-top: 1px solid #f3f4f6; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>⏰ Event Reminder</h1>
                        </div>
                        <div class="body">
                            <p>Hi there,</p>
                            <p>This is a friendly reminder about your upcoming event:</p>
                            <div class="event-card">
                                <h2>%s</h2>
                                <p>📅 <strong>When:</strong> %s</p>
                                %s
                            </div>
                            <p>Don't miss it! Open Flux Calendar to view details.</p>
                        </div>
                        <div class="footer">
                            <p>Flux Calendar — Your time, organized.</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(payload.getEventTitle()),
                eventTime,
                payload.getEventDescription() != null && !payload.getEventDescription().isBlank()
                        ? "<p>📝 <strong>Details:</strong> " + escapeHtml(payload.getEventDescription()) + "</p>"
                        : ""
        );
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
