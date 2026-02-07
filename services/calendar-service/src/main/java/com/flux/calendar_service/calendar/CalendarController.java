package com.flux.calendar_service.calendar;

import com.flux.calendar_service.calendar.dto.CalendarRequest;
import com.flux.calendar_service.calendar.dto.CalendarResponse;
import com.flux.calendar_service.calendar.dto.CalendarUpdateRequest;
import com.flux.calendar_service.calendar.dto.PrimaryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/calendars")
@RequiredArgsConstructor
@Tag(name = "Calendars", description = "Endpoints for managing user calendars")
public class CalendarController {
    private final CalendarService calendarService;

    @Operation(summary = "Create a new calendar", description = "Creates a new calendar for a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Calendar created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request (validation failed)")
    })
    @PostMapping
    public ResponseEntity<String> create(@RequestBody @Valid CalendarRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(calendarService.createCalendar(request));
    }

    @Operation(summary = "Get calendar by ID", description = "Retrieves detailed information about a specific calendar")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Calendar found"),
            @ApiResponse(responseCode = "404", description = "Calendar not found")
    })
    @GetMapping("/id/{id}")
    public ResponseEntity<CalendarResponse> getById(
            @Parameter(description = "ID of the calendar") @PathVariable String id) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(calendarService.getCalendarById(id));
    }

    @Operation(summary = "Get calendars by user ID", description = "Retrieves all calendars belonging to a specific user")
    @ApiResponse(responseCode = "200", description = "List of calendars (may be empty)")
    @GetMapping("/user/id/{id}")
    public ResponseEntity<List<CalendarResponse>> getByUserId(
            @Parameter(description = "ID of the user") @PathVariable String id) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(calendarService.getCalendarsByUserId(id));
    }

    @Operation(summary = "Get primary calendar", description = "Retrieves the primary calendar for a specific user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Primary calendar found"),
            @ApiResponse(responseCode = "404", description = "User or primary calendar not found")
    })
    @GetMapping("/user/primary/{id}")
    public ResponseEntity<CalendarResponse> getPrimaryCalendar(
            @Parameter(description = "ID of the user") @PathVariable String id) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(calendarService.getPrimaryCalendar(id));
    }

    @Operation(summary = "Get calendar by title", description = "Retrieves a specific calendar by its title for a given user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Calendar found"),
            @ApiResponse(responseCode = "404", description = "Calendar not found")
    })
    @GetMapping("/title/{title}/user/{id}/")
    public ResponseEntity<CalendarResponse> getByTitle(
            @Parameter(description = "ID of the user") @PathVariable String id,
            @Parameter(description = "Title of the calendar") @PathVariable String title) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(calendarService.getByTitle(id, title));
    }

    @Operation(summary = "Set primary calendar", description = "Sets a specific calendar as the primary one for a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Primary status update accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Calendar not found")
    })
    @PutMapping("/user/primary/{id}")
    public ResponseEntity<?> makePrimary(@Parameter(description = "ID of the calendar") @PathVariable String id,
            @RequestBody @Valid PrimaryRequest request) {
        calendarService.makePrimary(id, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @Operation(summary = "Update calendar", description = "Updates the details of an existing calendar")
    @ApiResponse(responseCode = "202", description = "Calendar update accepted")
    @PutMapping("/update/{id}")
    public ResponseEntity<?> update(
            @Parameter(description = "ID of the calendar") @PathVariable String id,
            @RequestBody CalendarUpdateRequest request) {
        calendarService.updateCalendar(id, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @Operation(summary = "Recover deleted calendar", description = "Restores a previously deleted calendar")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Calendar recovery accepted"),
            @ApiResponse(responseCode = "404", description = "Calendar not found")
    })
    @PutMapping("/recover/{id}")
    public ResponseEntity<?> recoverCalendar(@Parameter(description = "ID of the calendar") @PathVariable String id) {
        calendarService.recoverCalendar(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @Operation(summary = "Delete calendar", description = "Soft-deletes an existing calendar")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Calendar deletion accepted"),
            @ApiResponse(responseCode = "404", description = "Calendar not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCalendar(@Parameter(description = "ID of the calendar") @PathVariable String id) {
        calendarService.deleteCalendar(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}