package com.flux.calendar_service.scheduler;

import com.flux.calendar_service.client.UserClient;
import com.flux.calendar_service.email.EmailService;
import com.flux.calendar_service.event.Event;
import com.flux.calendar_service.event.EventRepository;
import com.flux.calendar_service.location.Location;
import com.flux.calendar_service.conference.Conference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventReminderScheduler {

    private final EventRepository eventRepository;
    private final EmailService emailService;
    private final UserClient userClient;

    @Scheduled(cron = "0 * * * * *") // Every minute
    @org.springframework.transaction.annotation.Transactional
    public void checkUpcomingEvents() {
        Instant now = Instant.now();
        Instant start = now.plus(30, java.time.temporal.ChronoUnit.MINUTES).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        Instant end = start.plus(1, java.time.temporal.ChronoUnit.MINUTES);

        List<Event> events = eventRepository.findByStartTimeBetween(start, end);

        for (Event event : events) {
            sendReminder(event);
        }
    }

    private void sendReminder(Event event) {
        String userId = event.getCalendar().getUserId();
        String email = userClient.getUserEmail(userId);

        if (email == null) {
            log.warn("User email not found for userId: {}", userId);
            return;
        }

        String subject = "Reminder: " + event.getTitle() + " starts in 30 minutes";
        String body = buildEmailBody(event);

        emailService.sendEmail(email, subject, body);
    }

    private String buildEmailBody(Event event) {

        String formattedTime = ZonedDateTime.ofInstant(event.getStartTime(), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("EEEE, MMM dd • HH:mm"));

        StringBuilder sb = new StringBuilder();

        sb.append("""
        <html>
        <body style="font-family:Arial, sans-serif;background:#f4f6f9;padding:40px;">
        <div style="max-width:600px;margin:auto;background:white;padding:30px;
                    border-radius:12px;box-shadow:0 4px 12px rgba(0,0,0,0.1);">
        """);

        sb.append("<h2 style='color:#2c3e50;'>⏰ Event Reminder</h2>");
        sb.append("<p>Hi,</p>");
        sb.append("<p>Your event <b>").append(event.getTitle())
                .append("</b> will start in <b>30 minutes</b>.</p>");

        sb.append("<div style='background:#f8f9fa;padding:15px;border-radius:8px;margin-top:20px;'>");
        sb.append("<h3 style='margin-top:0;color:#34495e;'>Event Details</h3>");

        sb.append("<p><b>📅 Time:</b> ").append(formattedTime).append("</p>");

        Location location = event.getLocation();
        if (location != null) {
            sb.append("<p><b>📍 Location:</b> ").append(location.getPlaceName());
            if (location.getStreetAddress() != null) {
                sb.append(", ").append(location.getStreetAddress());
            }
            sb.append("</p>");
        }

        if (event.getDescription() != null && !event.getDescription().isEmpty()) {
            sb.append("<p><b>📝 Description:</b> ").append(event.getDescription()).append("</p>");
        }

        sb.append("</div>");

        Conference conference = event.getConference();
        if (conference != null) {

            String link = conference.getMeetLink() != null ?
                    conference.getMeetLink() : conference.getConferenceLink();

            if (link != null) {

                sb.append("""
                <div style="text-align:center;margin:30px 0;">
                <a href=\"""").append(link).append("""
                \" style="
                background:#3498db;
                color:white;
                padding:14px 28px;
                text-decoration:none;
                border-radius:8px;
                font-weight:bold;
                display:inline-block;">
                Join Meeting
                </a>
                </div>
                """);
            }
        }

        if (!event.getSubtasks().isEmpty()) {

            sb.append("<h3 style='color:#34495e;'>📋 Subtasks</h3>");
            sb.append("<ul style='padding-left:20px;'>");

            event.getSubtasks().forEach(subtask ->
                    sb.append("<li style='margin-bottom:6px;'>")
                            .append(subtask.getSubtask())
                            .append("</li>")
            );

            sb.append("</ul>");
        }

        sb.append("""
        <hr style="margin:30px 0">
        <p style="font-size:12px;color:#888;">
        This reminder was sent by <b>Flux Calendar</b>.
        </p>
        </div>
        </body>
        </html>
        """);

        return sb.toString();
    }
}
