package com.flux.calendar_service.member;

import com.flux.calendar_service.member.dto.MemberRequest;
import com.flux.calendar_service.member.dto.MemberResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;

    @GetMapping("/share/event/{eventId}/{email}")
    public ResponseEntity<?> inviteMember(@PathVariable String eventId, String email) {
        memberService.sendInviteUrl(email, eventId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/join/event/{eventId}/{email}")
    public ResponseEntity<String> joinToEvent(
            @PathVariable String eventId, String email,
            @RequestBody MemberRequest memberRequest
            ){
        return ResponseEntity
                .ok()
                .body(memberService.addMember(eventId, email, memberRequest));
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<MemberResponseDTO>> getMembersOfEvent(@PathVariable String eventId) {
        return ResponseEntity
                .ok()
                .body(memberService.getMemberByEventId(eventId));
    }

    @GetMapping("/delete/{memberId}")
    public ResponseEntity<?> deleteMember(
            @PathVariable String memberId,
            @RequestParam String userId
    ) {
        memberService.deleteMember(memberId, userId);
        return ResponseEntity.ok().build();
    }
}