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

import java.time.LocalDateTime;
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
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.plusMinutes(30).withSecond(0).withNano(0);
        LocalDateTime end = start.plusMinutes(1);

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
        StringBuilder sb = new StringBuilder();
        sb.append("<h1>Event Reminder</h1>");
        sb.append("<p>Hi,</p>");
        sb.append("<p>This is a reminder that your event <b>").append(event.getTitle())
                .append("</b> is starting in 30 minutes.</p>");

        sb.append("<h3>Details:</h3>");
        sb.append("<ul>");
        sb.append("<li><b>Time:</b> ")
                .append(event.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("</li>");

        Location location = event.getLocation();
        if (location != null) {
            sb.append("<li><b>Location:</b> ").append(location.getPlaceName());
            if (location.getStreetAddress() != null) {
                sb.append(", ").append(location.getStreetAddress());
            }
            sb.append("</li>");
        }

        if (event.getDescription() != null && !event.getDescription().isEmpty()) {
            sb.append("<li><b>Description:</b> ").append(event.getDescription()).append("</li>");
        }

        Conference conference = event.getConference();
        if (conference != null) {
            String link = conference.getMeetLink() != null ? conference.getMeetLink() : conference.getConferenceLink();
            if (link != null) {
                sb.append("<li><b>Meeting Link:</b> <a href=\"").append(link).append("\">Join Meeting</a></li>");
            }
        }

        sb.append("</ul>");

        if (!event.getTasks().isEmpty()) {
            sb.append("<h3>Tasks:</h3><ul>");
            event.getTasks().forEach(task -> sb.append("<li>").append(task.getTask()).append("</li>"));
            sb.append("</ul>");
        }

        sb.append("<p>Best regards,<br>Flux</p>");
        return sb.toString();
    }
}
