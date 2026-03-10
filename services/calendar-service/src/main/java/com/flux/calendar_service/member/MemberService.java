package com.flux.calendar_service.member;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberService {
    private final EmailService emailService;
    private final EventService eventService;
    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;

    @Value("${app.url}")
    private String appUrl;

    public void sendInviteUrl(String email, String eventId) {
        Event event = eventService.getEventByIdTypeEvent(eventId);
        String userId = event.getCalendar().getUserId();

        if (email == null) {
            log.warn("User email not found for userId: {}", userId);
            return;
        }

        String subject = "Invitation to Join Event";
        String body = buildEmailBody(event, generateJoinLink(event.getId(), email));

        emailService.sendEmail(email, subject, body, buildICS(event));
    }

    private String buildICS(Event event) {

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                        .withZone(ZoneOffset.UTC);

        String start = formatter.format(event.getStartTime());
        String end = formatter.format(event.getEndTime());

        return "BEGIN:VCALENDAR\n" +
                "VERSION:2.0\n" +
                "PRODID:-//Flux Calendar//EN\n" +
                "METHOD:REQUEST\n" +
                "BEGIN:VEVENT\n" +
                "UID:" + event.getId() + "@flux\n" +
                "DTSTAMP:" + start + "\n" +
                "DTSTART:" + start + "\n" +
                "DTEND:" + end + "\n" +
                "SUMMARY:" + event.getTitle() + "\n" +
                "DESCRIPTION:" + event.getDescription() + "\n" +
                "LOCATION:" + (event.getLocation() != null ? event.getLocation().getPlaceName() : "") + "\n" +
                "STATUS:CONFIRMED\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
    }

    private String generateJoinLink(String eventId, String email) {
        return appUrl + "/members/join/event/" + eventId + "/" + email;
    }

    private String buildEmailBody(Event event, String joinUrl) {

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM dd yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        String date = event.getStartTime()
                .atZone(ZoneId.systemDefault())
                .format(dateFormatter);

        String time = event.getStartTime()
                .atZone(ZoneId.systemDefault())
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
