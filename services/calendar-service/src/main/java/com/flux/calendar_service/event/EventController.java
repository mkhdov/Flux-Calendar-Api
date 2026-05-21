package com.flux.calendar_service.event;

import com.flux.calendar_service.event.dto.EventRequest;
import com.flux.calendar_service.event.dto.EventNotificationResponse;
import com.flux.calendar_service.event.dto.EventResponse;
import com.flux.calendar_service.event.dto.EventUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Event created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request (validation failed)"),
            @ApiResponse(responseCode = "404", description = "Calendar not found")
    })
    @PostMapping("/calendar/{calendarId}")
    public ResponseEntity<List<String>> createEvent(
            @Parameter(description = "ID of the calendar to add the event to") @PathVariable String calendarId,
            @RequestBody @Valid EventRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(eventService.createEvent(calendarId, request));
    }

    @Operation(summary = "Create an event with Google Meet", description = "Creates a new event and automatically generates a Google Meet link")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Event with Meet created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Calendar not found")
    })
    @PostMapping("/calendar/{calendarId}/add-meet")
    public ResponseEntity<List<String>> createEventWithMeet(
            @Parameter(description = "ID of the calendar to add the event to") @PathVariable String calendarId,
            @RequestBody @Valid EventRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(eventService.createEventWithGoogleMeet(calendarId, request));
    }

    @Operation(summary = "Create an event with Zoom", description = "Creates a new event and automatically generates a Zoom meeting link")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Event with Zoom created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Calendar not found"),
            @ApiResponse(responseCode = "401", description = "Zoom not connected")
    })
    @PostMapping("/calendar/{calendarId}/add-zoom")
    public ResponseEntity<List<String>> createEventWithZoom(
            @Parameter(description = "ID of the calendar to add the event to") @PathVariable String calendarId,
            @Parameter(description = "User ID from authentication", hidden = true) @RequestHeader(value = "X-User-Id") String userId,
            @RequestBody @Valid EventRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(eventService.createEventWithZoom(calendarId, request, userId));
    }

    /**
     * Get events by date range
     * GET /events/range?start=...&end=...
     */
    @Operation(summary = "Get events by date range", description = "Retrieves events within a specific time range for a given calendar")
    @ApiResponse(responseCode = "200", description = "List of events in the range (may be empty)")
    @GetMapping("/range")
    public ResponseEntity<List<EventResponse>> getEventsByDateRange(
            @Parameter(description = "Calendar ID") @RequestParam String calendarId,
            @Parameter(description = "Start time (ISO format; timezone-less values are treated as UTC)") @RequestParam Instant start,
            @Parameter(description = "End time (ISO format; timezone-less values are treated as UTC)") @RequestParam Instant end) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(eventService.getEventsByDateRange(calendarId, start, end));
    }

    @Operation(summary = "Get events by month", description = "Retrieves a simplified version of events for a specific month")
    @ApiResponse(responseCode = "200", description = "List of events for the month")
    @GetMapping("/month")
    public ResponseEntity<List<com.flux.calendar_service.event.dto.EventMonthResponse>> getEventsByMonth(
            @Parameter(description = "Calendar ID") @RequestParam String calendarId,
            @Parameter(description = "Year (e.g., 2023)") @RequestParam int year,
            @Parameter(description = "Month (1-12)") @RequestParam int month) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(eventService.getEventsByMonth(calendarId, year, month));
    }

    /**
     * Get event by ID
     * GET /events/{id}
     */
    @Operation(summary = "Get event by ID", description = "Retrieves detailed information about a specific event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Event found"),
            @ApiResponse(responseCode = "404", description = "Event not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEventById(
            @Parameter(description = "ID of the event to retrieve") @PathVariable String id) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(eventService.getEventById(id));
    }

    /**
     * Lightweight event payload for reminders
     * GET /events/{id}/notification
     */
    @Operation(summary = "Get lightweight event for notifications", description = "Returns a minimized event payload suitable for reminder email rendering (no attachments)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Event found"),
            @ApiResponse(responseCode = "404", description = "Event not found")
    })
    @GetMapping("/{id}/notification")
    public ResponseEntity<EventNotificationResponse> getEventNotificationById(
            @Parameter(description = "ID of the event to retrieve") @PathVariable String id) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(eventService.getEventNotificationById(id));
    }

    /**
     * Get all events for a specific calendar
     * GET /events/calendar/{calendarId}
     */
    @Operation(summary = "Get events by calendar", description = "Retrieves all events associated with a specific calendar")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of events (may be empty)"),
            @ApiResponse(responseCode = "404", description = "Calendar not found")
    })
    @GetMapping("/calendar/{calendarId}")
    public ResponseEntity<List<EventResponse>> getEventsByCalendar(
            @Parameter(description = "ID of the calendar") @PathVariable String calendarId) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(eventService.getEventsByCalendarId(calendarId));
    }

    /**
     * Get all MEETING type events for a specific calendar
     * GET /events/calendar/{calendarId}/meetings
     */
    @Operation(summary = "Get meetings by calendar", description = "Retrieves all events of type MEETING for a specific calendar, optionally filtered by tab (today/upcoming/past)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of meetings (may be empty)"),
            @ApiResponse(responseCode = "404", description = "Calendar not found")
    })
    @GetMapping("/calendar/{calendarId}/meetings")
    public ResponseEntity<List<EventResponse>> getMeetingsByCalendar(
            @Parameter(description = "ID of the calendar") @PathVariable String calendarId,
            @Parameter(description = "Filter: today | upcoming | past | all (default: all)") @RequestParam(required = false, defaultValue = "all") String filter) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(eventService.getMeetingsByCalendarId(calendarId, filter));
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Event update accepted"),
            @ApiResponse(responseCode = "404", description = "Event not found")
    })
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Event deletion accepted"),
            @ApiResponse(responseCode = "404", description = "Event not found")
    })
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Location attached successfully"),
            @ApiResponse(responseCode = "404", description = "Event or location not found")
    })
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Conference attached successfully"),
            @ApiResponse(responseCode = "404", description = "Event or conference not found")
    })
    @PutMapping("/{eventId}/conference/{conferenceId}")
    public ResponseEntity<?> attachConference(
            @Parameter(description = "ID of the event") @PathVariable String eventId,
            @Parameter(description = "ID of the conference") @PathVariable String conferenceId) {
        eventService.attachConference(eventId, conferenceId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * Add Zoom meeting to existing event
     * POST /events/add-zoom/{id}
     */
    @Operation(summary = "Add Zoom to existing event", description = "Adds a Zoom meeting to an existing event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Zoom meeting added successfully"),
            @ApiResponse(responseCode = "404", description = "Event not found"),
            @ApiResponse(responseCode = "401", description = "Zoom not connected")
    })
    @PostMapping("/add-zoom/{id}")
    public ResponseEntity<EventResponse> addZoomToEvent(
            @Parameter(description = "ID of the event") @PathVariable String id,
            @Parameter(description = "User ID from authentication", hidden = true) @RequestHeader(value = "X-User-Id") String userId) {
        eventService.addZoomToExistingEvent(id, userId);
        return ResponseEntity.status(HttpStatus.OK).body(eventService.getEventById(id));
    }

    /**
     * Remove Zoom meeting from event
     * DELETE /events/remove-zoom/{id}
     */
    @Operation(summary = "Remove Zoom from event", description = "Removes the Zoom meeting from an event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Zoom meeting removed successfully"),
            @ApiResponse(responseCode = "404", description = "Event not found")
    })
    @DeleteMapping("/remove-zoom/{id}")
    public ResponseEntity<?> removeZoomFromEvent(
            @Parameter(description = "ID of the event") @PathVariable String id,
            @Parameter(description = "User ID from authentication", hidden = true) @RequestHeader(value = "X-User-Id") String userId) {
        eventService.removeZoomFromEvent(id, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * Sync events from Google Calendar
     * POST /events/sync/google/{calendarId}
     */
    @Operation(summary = "Sync events from Google", description = "Synchronizes events from Google Calendar to the local calendar")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Sync started successfully"),
            @ApiResponse(responseCode = "404", description = "Calendar not found")
    })
    @PostMapping("/sync/google/{calendarId}")
    public ResponseEntity<?> syncFromGoogle(
            @Parameter(description = "ID of the local calendar to sync to") @PathVariable String calendarId) {
        eventService.syncEventsFromGoogle(calendarId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
