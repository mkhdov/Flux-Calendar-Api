package com.flux.calendar_service.member;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.flux.calendar_service.conference.Conference;
import com.flux.calendar_service.email.EmailService;
import com.flux.calendar_service.event.Event;
import com.flux.calendar_service.event.EventService;
import com.flux.calendar_service.exception.EmptyFieldException;
import com.flux.calendar_service.exception.ResourceNotFoundException;
import com.flux.calendar_service.exception.UnauthorizedException;
import com.flux.calendar_service.location.Location;
import com.flux.calendar_service.member.dto.MemberRequest;
import com.flux.calendar_service.member.dto.MemberResponseDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberService {
    private final EmailService emailService;
    private final EventService eventService;
    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;

    @Value("${app.share_url}")
    private String appUrl;

    @Value("${spring.mail.username}")
    private String senderEmail;

    public void sendInviteUrl(String email, String eventId) {
        Event event = eventService.getEventByIdTypeEvent(eventId);
        String userId = event.getCalendar().getUserId();

        if (email == null) {
            log.warn("User email not found for userId: {}", userId);
            return;
        }

        String subject = String.format(
                "You're invited: %s on %s",
                event.getTitle(),
                event.getStartTime()
        );
        String joinLink = generateJoinLink(event.getId(), email);
        String body = buildEmailBody(event, joinLink);

        emailService.sendEmail(email, subject, body, buildICS(event, email, joinLink));
    }

    private String buildICS(Event event, String attendeeEmail, String joinUrl) {
        ZoneId eventZone = resolveEventZone(event);
        DateTimeFormatter utcFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC);
        DateTimeFormatter localFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
                .withLocale(Locale.US);

        String dtStamp = utcFormatter.format(Instant.now());
        String dtStart = event.getStartTime().atZone(eventZone).format(localFormatter);
        String dtEnd = event.getEndTime().atZone(eventZone).format(localFormatter);
        String organizerEmail = resolveOrganizerEmail(event);
        String organizerName = resolveOrganizerName(event);
        String location = event.getLocation() != null ? event.getLocation().getPlaceName() : "";

        StringBuilder ics = new StringBuilder();
        appendIcsLine(ics, "BEGIN:VCALENDAR");
        appendIcsLine(ics, "PRODID:-//Flux Calendar//EN");
        appendIcsLine(ics, "VERSION:2.0");
        appendIcsLine(ics, "CALSCALE:GREGORIAN");
        appendIcsLine(ics, "METHOD:REQUEST");
        appendIcsLine(ics, "BEGIN:VEVENT");
        appendIcsLine(ics, "UID:" + escapeIcsText(event.getId() + "@flux"));
        appendIcsLine(ics, "DTSTAMP:" + dtStamp);
        appendIcsLine(ics, "DTSTART;TZID=" + eventZone.getId() + ":" + dtStart);
        appendIcsLine(ics, "DTEND;TZID=" + eventZone.getId() + ":" + dtEnd);
        appendIcsLine(ics, "SUMMARY:" + escapeIcsText(event.getTitle()));
        appendIcsLine(ics, "DESCRIPTION:" + escapeIcsText(event.getDescription()));
        appendIcsLine(ics, "LOCATION:" + escapeIcsText(location));
        appendIcsLine(ics, "STATUS:CONFIRMED");
        appendIcsLine(ics, "SEQUENCE:" + resolveSequence(event));
        appendIcsLine(ics, "URL:" + escapeIcsText(joinUrl));
        appendIcsLine(ics, "ORGANIZER;CN=" + escapeIcsParam(organizerName) + ":mailto:" + organizerEmail);
        appendIcsLine(ics, "ATTENDEE;CN=" + escapeIcsParam(attendeeEmail)
                + ";ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:" + attendeeEmail);
        appendIcsLine(ics, "END:VEVENT");
        appendIcsLine(ics, "END:VCALENDAR");

        return ics.toString();
    }

    private String generateJoinLink(String eventId, String email) {
        return appUrl + "/join?eventId=" + eventId + "&email=" + email;
    }

    private String buildEmailBody(Event event, String joinUrl) {

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM dd yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        ZoneId zoneId = ZoneId.of(event.getCalendar().getTimezone());

        String date = event.getStartTime()
            .atZone(zoneId)
            .format(dateFormatter);

        String time = event.getStartTime()
            .atZone(zoneId)
            .format(timeFormatter);

        StringBuilder sb = new StringBuilder();

        sb.append("""
        <html>
        <body style="font-family:Arial, sans-serif;background:#f4f6f9;padding:40px;">
        <div style="max-width:600px;margin:auto;background:white;padding:30px;
                    border-radius:12px;box-shadow:0 4px 12px rgba(0,0,0,0.1);">
        """);

        sb.append("<h2 style='color:#2c3e50;'>📅 Event Invitation</h2>");

        sb.append("<p>You have been invited to join the following event:</p>");

        sb.append("<div style='background:#f8f9fa;padding:20px;border-radius:8px;'>");

        sb.append("<p><b>Event:</b> ").append(event.getTitle()).append("</p>");
        sb.append("<p><b>Date:</b> ").append(date).append("</p>");
        sb.append("<p><b>Time:</b> ").append(time).append("</p>");

        Location location = event.getLocation();

        if (location != null && location.getPlaceName() != null) {

            sb.append("<p><b>📍 Location:</b> ")
                    .append(location.getPlaceName());

            if (location.getStreetAddress() != null) {
                sb.append(", ").append(location.getStreetAddress());
            }

            sb.append("</p>");

        } else {
            sb.append("<p><b>📍 Location:</b> Online</p>");
        }

        sb.append("</div>");

        // Join Event Button (uses joinUrl)
        if (joinUrl != null) {

            sb.append("""
            <div style="text-align:center;margin:30px 0;">
            <a href=\"""").append(joinUrl).append("""
            \" style="
            background:#3498db;
            color:white;
            padding:14px 28px;
            text-decoration:none;
            border-radius:8px;
            font-weight:bold;
            display:inline-block;">
            Join Event
            </a>
            </div>
            """);
        }

        // Conference link if exists
        Conference conference = event.getConference();

        if (conference != null) {

            String joinLink = conference.getMeetLink() != null
                    ? conference.getMeetLink()
                    : conference.getConferenceLink();

            if (joinLink != null) {

                sb.append("<p style='text-align:center;'>Or join via meeting link:</p>");

                sb.append("<p style='text-align:center;'>");
                sb.append("<a href=\"").append(joinLink).append("\">")
                        .append(joinLink)
                        .append("</a></p>");
            }
        }

        sb.append("""
        <hr style="margin:30px 0">
        <p style="font-size:13px;color:#666;">
        By joining, you will be added as a member of this event and receive future updates.
        </p>
        
        <p style="font-size:12px;color:#999;">
        If you believe this invitation was sent in error, you may ignore this email.
        </p>
        
        <p style="margin-top:30px;">
        Best regards,<br>
        <b>Flux</b><br>
        Calendar Service Team
        </p>
        
        </div>
        </body>
        </html>
        """);

        return sb.toString();
    }

    private ZoneId resolveEventZone(Event event) {
        String timezone = Optional.ofNullable(event.getCalendar())
                .map(com.flux.calendar_service.calendar.Calendar::getTimezone)
                .filter(tz -> !tz.isBlank())
                .orElse("UTC");
        return ZoneId.of(timezone);
    }

    private String resolveOrganizerEmail(Event event) {
        return event.getMembers().stream()
                .filter(Member::isOwner)
                .map(Member::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .findFirst()
                .orElse(senderEmail);
    }

    private String resolveOrganizerName(Event event) {
        return event.getMembers().stream()
                .filter(Member::isOwner)
                .map(Member::getFullName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse("Flux Calendar");
    }

    private int resolveSequence(Event event) {
        Instant source = event.getUpdatedAt() != null ? event.getUpdatedAt() : event.getCreatedAt();
        if (source == null) {
            return 0;
        }
        return (int) Math.floorMod(source.getEpochSecond(), Integer.MAX_VALUE);
    }

    private void appendIcsLine(StringBuilder builder, String line) {
        builder.append(foldIcsLine(line)).append("\r\n");
    }

    private String foldIcsLine(String line) {
        int maxLength = 75;
        if (line.length() <= maxLength) {
            return line;
        }

        StringBuilder folded = new StringBuilder();
        int index = 0;
        while (index < line.length()) {
            int end = Math.min(index + maxLength, line.length());
            if (index > 0) {
                folded.append("\r\n ");
            }
            folded.append(line, index, end);
            index = end;
        }
        return folded.toString();
    }

    private String escapeIcsText(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    private String escapeIcsParam(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace(":", "\\:");
    }

    @Transactional
    @CacheEvict(cacheNames = {"events:by-range", "events:month", "events:detail"}, allEntries = true)
    public String addMember(String eventId, String email, MemberRequest request) {
        Event event = eventService.getEventByIdTypeEvent(eventId);
        Member member = Member.builder()
                .email(email)
                .fullName(request.fullName())
                .isOwner(false)
                .event(event)
                .build();

        return memberRepository.save(member).getId();
    }

    public List<MemberResponseDTO> getMemberByEventId(String evenId) {
        if(evenId.isBlank()){
            throw new EmptyFieldException("Even's id can not be empty!");
        }

        return memberRepository.findAllMembersByEventId(evenId)
                .stream()
                .map(memberMapper::toMemberResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(cacheNames = {"events:by-range", "events:month"}, allEntries = true)
    public void deleteMember(String memberId, String currentMemberId) {
        if(isItOwner(currentMemberId)) {
            Member member = memberRepository.findMemberById(memberId)
                    .orElseThrow(() -> new ResourceNotFoundException("Member not found!"));
            if(!member.isOwner()){
                memberRepository.delete(member);
                return;
            }

            throw new UnauthorizedException("Not possible to remove owner of event!");
        }
        else {
            throw new UnauthorizedException("Only owner can remove members");
        }
    }

    private boolean isItOwner(String memberId) {
        Member member = memberRepository.findMemberById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found!"));

        return member.isOwner();
    }
}
