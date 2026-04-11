package com.flux.calendar_service.conference;

import com.flux.calendar_service.conference.dto.ConferenceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/conference")
@RequiredArgsConstructor
@Tag(name = "Conferences", description = "Endpoints for managing event conferences (Google Meet)")
public class ConferenceController {
    private final com.flux.calendar_service.event.EventService eventService;

    @Operation(summary = "Add Google Meet to event", description = "Adds a Google Meet conference link to an existing event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Google Meet added successfully"),
            @ApiResponse(responseCode = "404", description = "Event not found")
    })
    @PostMapping("/{id}/add-meet")
    public org.springframework.http.ResponseEntity<Void> addGoogleMeet(
            @Parameter(description = "ID of the event") @PathVariable String id) {
        eventService.addGoogleMeetToExistingEvent(id);
        return org.springframework.http.ResponseEntity.ok().build();
    }

    @Operation(summary = "Remove Google Meet from event", description = "Removes the Google Meet conference link from an existing event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Google Meet removed successfully"),
            @ApiResponse(responseCode = "404", description = "Event not found")
    })
    @DeleteMapping("/{id}/remove-meet")
    public org.springframework.http.ResponseEntity<Void> removeGoogleMeet(
            @Parameter(description = "ID of the event") @PathVariable String id) {
        eventService.removeGoogleMeetFromEvent(id);
        return org.springframework.http.ResponseEntity.ok().build();
    }
}
