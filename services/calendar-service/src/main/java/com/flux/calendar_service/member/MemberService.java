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

        emailService.sendEmail(email, subject, body);
    }

    private String generateJoinLink(String eventId, String email) {
        return appUrl + "/members/join/event/" + eventId + "/" + email;
    }

    private String buildEmailBody(Event event, String joinUrl) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        StringBuilder sb = new StringBuilder();

        sb.append("<h2>You have been invited to join the following event:</h2>");

        sb.append("<p><b>Event:</b> ").append(event.getTitle()).append("<br>");
        sb.append("<b>Date:</b> ").append(event.getStartTime().format(dateFormatter)).append("<br>");
        sb.append("<b>Time:</b> ").append(event.getStartTime().format(timeFormatter)).append("<br>");

        // Location or Online
        Location location = event.getLocation();
        if (location != null && location.getPlaceName() != null) {
            sb.append("<b>Location:</b> ").append(location.getPlaceName());
            if (location.getStreetAddress() != null) {
                sb.append(", ").append(location.getStreetAddress());
            }
        } else {
            sb.append("<b>Location:</b> Online");
        }
        sb.append("</p>");

        // Join URL (from conference)
        Conference conference = event.getConference();
        String joinLink = joinUrl;
        if (conference != null) {
            joinLink = conference.getMeetLink() != null
                    ? conference.getMeetLink()
                    : conference.getConferenceLink();
        }

        if (joinLink != null) {
            sb.append("<p>To confirm your participation and view the event details, please use the link below:</p>");
            sb.append("<p>👉 <a href=\"").append(joinLink).append("\"><b>Join Event</b></a></p>");
        }

        sb.append("<p>By joining, you will be added as a member of this event and receive future updates.</p>");
        sb.append("<p>If you believe this invitation was sent in error, you may ignore this email.</p>");

        sb.append("<p>Best regards,<br>");
        sb.append("Vpire Max<br>");
        sb.append("Calendar Service Team</p>");

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
