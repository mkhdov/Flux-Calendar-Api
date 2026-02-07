package com.flux.calendar_service.member;

import com.flux.calendar_service.member.dto.MemberRequest;
import com.flux.calendar_service.member.dto.MemberResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
@Tag(name = "Members", description = "Endpoints for managing event members and invitations")
public class MemberController {
    private final MemberService memberService;

    @GetMapping("/share/event/{eventId}/{email}")
    @Operation(summary = "Invite member to event", description = "Sends an invitation email to a user to join an event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invitation sent successfully"),
            @ApiResponse(responseCode = "404", description = "Event not found"),
            @ApiResponse(responseCode = "400", description = "Invalid email address"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> inviteMember(
            @Parameter(description = "Event ID to invite member to") @PathVariable String eventId,
            @Parameter(description = "Email address of the member to invite") @PathVariable String email) {
        memberService.sendInviteUrl(email, eventId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/join/event/{eventId}/{email}")
    @Operation(summary = "Join event", description = "Adds a member to an event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully joined the event"),
            @ApiResponse(responseCode = "404", description = "Event not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request or already a member"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<String> joinToEvent(
            @Parameter(description = "Event ID to join") @PathVariable String eventId,
            @Parameter(description = "Email address of the member") @PathVariable String email,
            @Parameter(description = "Member details") @RequestBody MemberRequest memberRequest) {
        return ResponseEntity
                .ok()
                .body(memberService.addMember(eventId, email, memberRequest));
    }

    @GetMapping("/event/{eventId}")
    @Operation(summary = "Get event members", description = "Retrieves all members of a specific event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved members list"),
            @ApiResponse(responseCode = "404", description = "Event not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<MemberResponseDTO>> getMembersOfEvent(
            @Parameter(description = "Event ID") @PathVariable String eventId) {
        return ResponseEntity
                .ok()
                .body(memberService.getMemberByEventId(eventId));
    }

    @GetMapping("/delete/{memberId}")
    @Operation(summary = "Delete member", description = "Removes a member from an event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully deleted member"),
            @ApiResponse(responseCode = "404", description = "Member not found"),
            @ApiResponse(responseCode = "403", description = "Forbidden - only event owner can remove members"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> deleteMember(
            @Parameter(description = "Member ID to delete") @PathVariable String memberId,
            @Parameter(description = "User ID of the requesting user") @RequestParam String userId) {
        memberService.deleteMember(memberId, userId);
        return ResponseEntity.ok().build();
    }
}