package com.flux.notification_service.notification;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
            DateTimeFormatter.ofPattern("EEE, MMM d, yyyy • hh:mm a 'UTC'")
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
        String title = safe(payload.getEventTitle(), "Calendar Event");
        String whenLine = formatWhen(payload);

        String descriptionRow = payload.getEventDescription() != null && !payload.getEventDescription().isBlank()
                ? buildKeyValueRow("Details", escapeHtml(payload.getEventDescription()))
                : "";

        String locationRow = payload.getEventLocation() != null && !payload.getEventLocation().isBlank()
                ? buildKeyValueRow("Location", escapeHtml(payload.getEventLocation()))
                : "";

        String meetingUrl = pickMeetingUrl(payload);
        String meetingType = detectMeetingType(payload, meetingUrl);
        String buttonsRow = buildButtonsRow(meetingType, meetingUrl, payload.getEventLocation());
        String meetingInfoBlock = buildMeetingInfoBlock(payload, meetingType);
        String subtasksBlock = buildSubtasksBlock(payload.getSubtasks());

        return """
                <!doctype html>
                <html>
                  <head>
                    <meta charset="utf-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>Event Reminder</title>
                  </head>
                  <body style="margin:0;padding:0;background:#f6f7fb;">
                    <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">
                      Reminder: %s
                    </div>
                    <table role="presentation" cellpadding="0" cellspacing="0" width="100%%" style="background:#f6f7fb;padding:18px 10px;">
                      <tr>
                        <td align="center">
                          <table role="presentation" cellpadding="0" cellspacing="0" width="100%%" style="max-width:600px;background:#ffffff;border-radius:14px;overflow:hidden;border:1px solid #eef0f6;">
                            <tr>
                              <td style="padding:22px 22px;background:linear-gradient(135deg,#6366f1,#8b5cf6);color:#ffffff;">
                                <div style="font-family:Segoe UI,Roboto,Arial,sans-serif;font-size:18px;font-weight:700;line-height:1.2;">Event Reminder</div>
                                <div style="font-family:Segoe UI,Roboto,Arial,sans-serif;font-size:13px;opacity:0.9;margin-top:6px;">%s</div>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:22px 22px 6px 22px;font-family:Segoe UI,Roboto,Arial,sans-serif;color:#111827;">
                                <div style="font-size:20px;font-weight:700;line-height:1.25;">%s</div>
                                <div style="margin-top:10px;font-size:14px;color:#374151;line-height:1.45;">This is a reminder for your upcoming event.</div>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:0 22px 10px 22px;">
                                <table role="presentation" cellpadding="0" cellspacing="0" width="100%%" style="background:#f8f7ff;border:1px solid #ecebff;border-radius:12px;">
                                  <tr>
                                    <td style="padding:14px 14px 6px 14px;font-family:Segoe UI,Roboto,Arial,sans-serif;">
                                      %s
                                      %s
                                      %s
                                    </td>
                                  </tr>
                                  %s
                                  %s
                                </table>
                              </td>
                            </tr>
                            %s
                            <tr>
                              <td style="padding:14px 22px 20px 22px;font-family:Segoe UI,Roboto,Arial,sans-serif;color:#6b7280;font-size:12px;text-align:center;">
                                Flux Calendar — Your time, organized.
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(
                escapeHtml(title),
                escapeHtml(whenLine),
                escapeHtml(title),
                buildKeyValueRow("When", escapeHtml(whenLine)),
                descriptionRow,
                locationRow,
                buttonsRow,
                meetingInfoBlock,
                subtasksBlock
        );
    }

    private String safe(String val, String fallback) {
        if (val == null || val.isBlank()) return fallback;
        return val;
    }

    private String formatWhen(NotificationPayload payload) {
        Boolean allDay = payload.getAllDay();
        if (Boolean.TRUE.equals(allDay) && payload.getEventStartTime() != null) {
            return FORMATTER.format(payload.getEventStartTime()).split("•")[0].trim() + " (All day)";
        }

        if (payload.getEventStartTime() == null && payload.getEventEndTime() == null) {
            return "Not specified";
        }
        if (payload.getEventStartTime() != null && payload.getEventEndTime() == null) {
            return FORMATTER.format(payload.getEventStartTime());
        }
        if (payload.getEventStartTime() == null) {
            return "Ends: " + FORMATTER.format(payload.getEventEndTime());
        }
        return FORMATTER.format(payload.getEventStartTime()) + " — " + FORMATTER.format(payload.getEventEndTime());
    }

    private String buildKeyValueRow(String key, String valueHtml) {
        return """
                <div style="margin:0 0 8px 0;">
                  <div style="font-size:12px;color:#6b7280;font-weight:600;text-transform:uppercase;letter-spacing:0.02em;">%s</div>
                  <div style="font-size:14px;color:#111827;line-height:1.45;margin-top:3px;">%s</div>
                </div>
                """.formatted(escapeHtml(key), valueHtml);
    }

    private String pickMeetingUrl(NotificationPayload payload) {
        if (payload.getMeetingUrl() != null && !payload.getMeetingUrl().isBlank()) {
            return payload.getMeetingUrl().trim();
        }

        String location = payload.getEventLocation();
        if (location == null) return null;
        String loc = location.trim();
        if (loc.startsWith("http://") || loc.startsWith("https://")) {
            return loc;
        }
        return null;
    }

    private String detectMeetingType(NotificationPayload payload, String meetingUrl) {
        if (payload.getMeetingType() != null && !payload.getMeetingType().isBlank()) {
            return payload.getMeetingType().trim();
        }
        if (meetingUrl == null) return null;
        String url = meetingUrl.toLowerCase();
        if (url.contains("meet.google.com")) return "GOOGLE_MEET";
        if (url.contains("zoom.us")) return "ZOOM";
        return null;
    }

    private String buildButtonsRow(String meetingType, String meetingUrl, String locationText) {
        String joinLabel = switch (meetingType != null ? meetingType : "") {
            case "GOOGLE_MEET" -> "Join Google Meet";
            case "ZOOM" -> "Join Zoom";
            default -> "Join meeting";
        };

        String joinButton = meetingUrl != null && !meetingUrl.isBlank()
                ? buttonHtml(meetingUrl, joinLabel, "#2563eb")
                : "";

        String mapsUrl = buildMapsUrl(locationText);
        String mapButton = mapsUrl != null ? buttonHtml(mapsUrl, "Open in Maps", "#10b981") : "";

        if (joinButton.isBlank() && mapButton.isBlank()) {
            return "";
        }

        return """
                <tr>
                  <td style="padding:0 14px 14px 14px;">
                    %s
                    %s
                  </td>
                </tr>
                """.formatted(
                joinButton.isBlank() ? "" : "<div style=\"margin-top:10px;\">" + joinButton + "</div>",
                mapButton.isBlank() ? "" : "<div style=\"margin-top:10px;\">" + mapButton + "</div>"
        );
    }

    private String buttonHtml(String url, String label, String background) {
        String safeUrl = escapeHtml(url);
        String safeLabel = escapeHtml(label);
        return """
                <a href="%s" style="display:block;background:%s;color:#ffffff;text-decoration:none;font-family:Segoe UI,Roboto,Arial,sans-serif;font-size:14px;font-weight:700;padding:12px 14px;border-radius:10px;text-align:center;">
                  %s
                </a>
                """.formatted(safeUrl, background, safeLabel);
    }

    private String buildMapsUrl(String locationText) {
        if (locationText == null) return null;
        String loc = locationText.trim();
        if (loc.isBlank()) return null;
        if (loc.startsWith("http://") || loc.startsWith("https://")) return null;

        String q = URLEncoder.encode(loc, StandardCharsets.UTF_8);
        return "https://www.google.com/maps/search/?api=1&query=" + q;
    }

    private String buildSubtasksBlock(List<NotificationSubtask> subtasks) {
        if (subtasks == null || subtasks.isEmpty()) return "";

        String items = subtasks.stream()
                .limit(12)
                .map(s -> {
                    String status = s.isDone() ? "Done" : "Pending";
                    String title = s.subtask() != null ? s.subtask() : "";
                    return """
                            <tr>
                              <td style="padding:8px 14px;border-top:1px solid #ecebff;font-family:Segoe UI,Roboto,Arial,sans-serif;">
                                <div style="font-size:14px;color:#111827;line-height:1.35;">%s</div>
                                <div style="font-size:12px;color:#6b7280;margin-top:2px;">%s</div>
                              </td>
                            </tr>
                            """.formatted(escapeHtml(title), escapeHtml(status));
                })
                .collect(java.util.stream.Collectors.joining());

        return """
                <tr>
                  <td style="padding:0 14px 8px 14px;font-family:Segoe UI,Roboto,Arial,sans-serif;">
                    <div style="font-size:12px;color:#6b7280;font-weight:600;text-transform:uppercase;letter-spacing:0.02em;margin-top:6px;">Subtasks</div>
                  </td>
                </tr>
                %s
                """.formatted(items);
    }

    private String buildMeetingInfoBlock(NotificationPayload payload, String meetingType) {
        if (meetingType == null || meetingType.isBlank()) return "";

        StringBuilder rows = new StringBuilder();

        if ("GOOGLE_MEET".equals(meetingType)) {
            if (payload.getMeetingCode() != null && !payload.getMeetingCode().isBlank()) {
                rows.append(buildKeyValueRow("Meeting code", escapeHtml(payload.getMeetingCode())));
            }
            if (payload.getDialInPhone() != null && !payload.getDialInPhone().isBlank()) {
                rows.append(buildKeyValueRow("Dial-in", escapeHtml(payload.getDialInPhone())));
            }
            if (payload.getDialInPin() != null && !payload.getDialInPin().isBlank()) {
                rows.append(buildKeyValueRow("PIN", escapeHtml(payload.getDialInPin())));
            }
        }

        if ("ZOOM".equals(meetingType)) {
            if (payload.getMeetingPassword() != null && !payload.getMeetingPassword().isBlank()) {
                rows.append(buildKeyValueRow("Password", escapeHtml(payload.getMeetingPassword())));
            }
        }

        String html = rows.toString().trim();
        if (html.isEmpty()) return "";
        return """
                <tr>
                  <td style="padding:0 14px 6px 14px;font-family:Segoe UI,Roboto,Arial,sans-serif;">
                    <div style="font-size:12px;color:#6b7280;font-weight:600;text-transform:uppercase;letter-spacing:0.02em;margin-top:4px;">Meeting details</div>
                  </td>
                </tr>
                <tr>
                  <td style="padding:0 14px 10px 14px;font-family:Segoe UI,Roboto,Arial,sans-serif;">
                    %s
                  </td>
                </tr>
                """.formatted(html);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
