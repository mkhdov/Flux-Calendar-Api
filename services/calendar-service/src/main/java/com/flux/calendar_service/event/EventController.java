package com.flux.calendar_service.event;

import com.flux.calendar_service.event.dto.EventRequest;
import com.flux.calendar_service.event.dto.EventResponse;
import com.flux.calendar_service.event.dto.EventUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Endpoints for managing calendar events")
public class EventController {
    private final EventService eventService;

    /**
     * Create a new event for a specific calendar
     * POST /events/calendar/{calendarId}
     */
    @Operation(summary = "Create a new event", description = "Creates a new event for a specific calendar")
    @ApiResponse(responseCode = "201", description = "Event created successfully")
    @PostMapping("/calendar/{calendarId}")
    public ResponseEntity<String> createEvent(
            @Parameter(description = "ID of the calendar to add the event to") @PathVariable String calendarId,
            @RequestBody @Valid EventRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(eventService.createEvent(calendarId, request));
    }

    @Operation(summary = "Create an event with Google Meet", description = "Creates a new event and automatically generates a Google Meet link")
    @ApiResponse(responseCode = "201", description = "Event with Meet created successfully")
    @PostMapping("/calendar/{calendarId}/add-meet")
    public ResponseEntity<String> createEventWithMeet(
            @Parameter(description = "ID of the calendar to add the event to") @PathVariable String calendarId,
            @RequestBody @Valid EventRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(eventService.createEventWithGoogleMeet(calendarId, request));
    }

    /**
     * Get events by date range
     * GET /events/range?start=...&end=...
     */
    @Operation(summary = "Get events by date range", description = "Retrieves events within a specific time range")
    @GetMapping("/range")
    public ResponseEntity<List<EventResponse>> getEventsByDateRange(
            @Parameter(description = "Start time (ISO format)") @RequestParam LocalDateTime start,
            @Parameter(description = "End time (ISO format)") @RequestParam LocalDateTime end) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(eventService.getEventsByDateRange(start, end));
    }

    /**
     * Get event by ID
     * GET /events/{id}
     */
    @Operation(summary = "Get event by ID", description = "Retrieves detailed information about a specific event")
    @ApiResponse(responseCode = "200", description = "Event found")
    @ApiResponse(responseCode = "404", description = "Event not found")
    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEventById(
            @Parameter(description = "ID of the event to retrieve") @PathVariable String id) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(eventService.getEventById(id));
    }

    /**
     * Get all events for a specific calendar
     * GET /events/calendar/{calendarId}
     */
    @Operation(summary = "Get events by calendar", description = "Retrieves all events associated with a specific calendar")
    @GetMapping("/calendar/{calendarId}")
    public ResponseEntity<List<EventResponse>> getEventsByCalendar(
            @Parameter(description = "ID of the calendar") @PathVariable String calendarId) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(eventService.getEventsByCalendarId(calendarId));
    }

    /**
     * Get all events for a specific user (across all their calendars)
     * GET /events/user/{userId}
     */
    @Operation(summary = "Get events by user", description = "Retrieves all events for a specific user across all their calendars")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<EventResponse>> getEventsByUser(
            @Parameter(description = "ID of the user") @PathVariable String userId) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(eventService.getEventsByUserId(userId));
    }

    /**
     * Update an existing event
     * PUT /events/{id}
     */
    @Operation(summary = "Update an event", description = "Updates the details of an existing event")
    @ApiResponse(responseCode = "202", description = "Event update accepted")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateEvent(
            @Parameter(description = "ID of the event to update") @PathVariable String id,
            @RequestBody EventUpdateRequest request) {
        eventService.updateEvent(id, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * Delete an event
     * DELETE /events/{id}
     */
    @Operation(summary = "Delete an event", description = "Deletes an existing event")
    @ApiResponse(responseCode = "202", description = "Event deletion accepted")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEvent(
            @Parameter(description = "ID of the event to delete") @PathVariable String id) {
        eventService.deleteEvent(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * Attach an existing location to an event
     * PUT /events/{eventId}/location/{locationId}
     */
    @Operation(summary = "Attach location to event", description = "Associates an existing location with an event")
    @PutMapping("/{eventId}/location/{locationId}")
    public ResponseEntity<?> attachLocation(
            @Parameter(description = "ID of the event") @PathVariable String eventId,
            @Parameter(description = "ID of the location") @PathVariable String locationId) {
        eventService.attachLocation(eventId, locationId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * Attach a conference to an event
     * PUT /events/{eventId}/conference/{conferenceId}
     */
    @Operation(summary = "Attach conference to event", description = "Associates an existing conference with an event")
    @PutMapping("/{eventId}/conference/{conferenceId}")
    public ResponseEntity<?> attachConference(
            @Parameter(description = "ID of the event") @PathVariable String eventId,
            @Parameter(description = "ID of the conference") @PathVariable String conferenceId) {
        eventService.attachConference(eventId, conferenceId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
