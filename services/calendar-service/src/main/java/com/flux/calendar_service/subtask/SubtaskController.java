package com.flux.calendar_service.subtask;

import com.flux.calendar_service.subtask.dto.SubtaskRequest;
import com.flux.calendar_service.subtask.dto.SubtaskResponse;
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

import java.util.List;

@RestController
@RequestMapping("/subtasks")
@RequiredArgsConstructor
@Tag(name = "Subtasks", description = "Endpoints for managing event subtasks")
public class SubtaskController {
    private final SubtaskService subtaskService;

    @Operation(summary = "Create a subtask for an event", description = "Creates a new subtask associated with a specific event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subtask created successfully"),
            @ApiResponse(responseCode = "404", description = "Event not found")
    })
    @PostMapping("/event/{eventId}")
    public ResponseEntity<String> createSubtask(
            @Parameter(description = "ID of the event") @PathVariable String eventId,
            @RequestBody String name) {
        return ResponseEntity.ok(subtaskService.createSubtask(eventId, name));
    }

    @Operation(summary = "Get subtasks by event ID", description = "Retrieves all subtasks associated with a specific event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of subtasks (may be empty)"),
            @ApiResponse(responseCode = "404", description = "Event not found")
    })
    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<SubtaskResponse>> getSubtasksByEventId(
            @Parameter(description = "ID of the event") @PathVariable String eventId) {
        return ResponseEntity.ok(subtaskService.getAllSubtasksByEventId(eventId));
    }

    @Operation(summary = "Get subtask by ID", description = "Retrieves detailed information about a specific subtask")
    @ApiResponse(responseCode = "200", description = "Subtask found")
    @ApiResponse(responseCode = "404", description = "Subtask not found")
    @GetMapping("/{id}")
    public ResponseEntity<SubtaskResponse> getSubtaskById(
            @Parameter(description = "ID of the subtask") @PathVariable String id) {
        return ResponseEntity.ok(subtaskService.getById(id));
    }

    @Operation(summary = "Toggle subtask status", description = "Toggles the 'done' status of a specific subtask")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Subtask status toggled successfully"),
            @ApiResponse(responseCode = "404", description = "Subtask not found")
    })
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<?> toggleSubtask(@Parameter(description = "ID of the subtask") @PathVariable String id) {
        SubtaskResponse subtask = subtaskService.getById(id);
        if (subtask.isDone()) {
            subtaskService.incompleteSubtask(id);
        } else {
            subtaskService.completeSubtask(id);
        }
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .build();
    }

    @Operation(summary = "Update a subtask", description = "Updates the details of an existing subtask")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subtask updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g. blank subtask text)"),
            @ApiResponse(responseCode = "404", description = "Subtask not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<SubtaskResponse> updateSubtask(
            @Parameter(description = "ID of the subtask") @PathVariable String id,
            @RequestBody @Valid SubtaskRequest request) {
        return ResponseEntity.ok(subtaskService.updateSubtask(id, request));
    }

    @Operation(summary = "Delete a subtask", description = "Deletes an existing subtask")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Subtask deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Subtask not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubtask(@Parameter(description = "ID of the subtask") @PathVariable String id) {
        subtaskService.deleteSubtask(id);
        return ResponseEntity.noContent().build();
    }
}
