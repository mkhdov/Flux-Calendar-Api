package com.flux.notification_service.reminder;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flux.notification_service.reminder.dto.ReminderRequest;
import com.flux.notification_service.reminder.dto.ReminderResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    /**
     * Create a single reminder.
     */
    @PostMapping
    public ResponseEntity<ReminderResponse> createReminder(@Valid @RequestBody ReminderRequest request) {
        ReminderResponse response = reminderService.createReminder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Create multiple reminders at once.
     */
    @PostMapping("/batch")
    public ResponseEntity<List<ReminderResponse>> createReminders(
            @Valid @RequestBody List<ReminderRequest> requests) {
        List<ReminderResponse> responses = reminderService.createReminders(requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    /**
     * Get all reminders for a specific event.
     */
    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<ReminderResponse>> getByEventId(@PathVariable String eventId) {
        List<ReminderResponse> responses = reminderService.getByEventId(eventId);
        return ResponseEntity.ok(responses);
    }

    /**
     * Get a single reminder by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReminderResponse> getById(@PathVariable String id) {
        ReminderResponse response = reminderService.getById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a single reminder.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReminder(@PathVariable String id) {
        reminderService.deleteReminder(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Delete all reminders for a specific event.
     */
    @DeleteMapping("/event/{eventId}")
    public ResponseEntity<Void> deleteByEventId(@PathVariable String eventId) {
        reminderService.deleteByEventId(eventId);
        return ResponseEntity.noContent().build();
    }
}
