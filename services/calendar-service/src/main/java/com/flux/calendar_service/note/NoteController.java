package com.flux.calendar_service.note;

import com.flux.calendar_service.note.dto.NoteRequest;
import com.flux.calendar_service.note.dto.NoteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notes")
@RequiredArgsConstructor
@Tag(name = "Notes", description = "Endpoints for managing notes attached to events or tasks")
public class NoteController {
    private final NoteService noteService;

    @Operation(summary = "Create a note for an event", description = "Creates a new note associated with a specific event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Note created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g. blank content)"),
            @ApiResponse(responseCode = "404", description = "Event not found")
    })
    @PostMapping("/event/{eventId}")
    public ResponseEntity<NoteResponse> createNoteForEvent(
            @Parameter(description = "ID of the event") @PathVariable String eventId,
            @RequestBody @Valid NoteRequest request) {
        return ResponseEntity.ok(noteService.createNoteForEvent(eventId, request));
    }

    @Operation(summary = "Create a note for a task", description = "Creates a new note associated with a specific task")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Note created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g. blank content)"),
            @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @PostMapping("/task/{taskId}")
    public ResponseEntity<NoteResponse> createNoteForTask(
            @Parameter(description = "ID of the task") @PathVariable String taskId,
            @RequestBody @Valid NoteRequest request) {
        return ResponseEntity.ok(noteService.createNoteForTask(taskId, request));
    }

    @Operation(summary = "Get notes by event ID", description = "Retrieves all notes associated with a specific event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of notes (may be empty)"),
            @ApiResponse(responseCode = "404", description = "Event not found")
    })
    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<NoteResponse>> getNotesByEventId(
            @Parameter(description = "ID of the event") @PathVariable String eventId) {
        return ResponseEntity.ok(noteService.getNotesByEventId(eventId));
    }

    @Operation(summary = "Get notes by task ID", description = "Retrieves all notes associated with a specific task")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of notes (may be empty)"),
            @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<NoteResponse>> getNotesByTaskId(
            @Parameter(description = "ID of the task") @PathVariable String taskId) {
        return ResponseEntity.ok(noteService.getNotesByTaskId(taskId));
    }

    @Operation(summary = "Get note by ID", description = "Retrieves detailed information about a specific note")
    @ApiResponse(responseCode = "200", description = "Note found")
    @ApiResponse(responseCode = "404", description = "Note not found")
    @GetMapping("/{id}")
    public ResponseEntity<NoteResponse> getNoteById(
            @Parameter(description = "ID of the note") @PathVariable String id) {
        return ResponseEntity.ok(noteService.getById(id));
    }

    @Operation(summary = "Update a note", description = "Updates the content of an existing note")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Note updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g. blank content)"),
            @ApiResponse(responseCode = "404", description = "Note not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<NoteResponse> updateNote(
            @Parameter(description = "ID of the note") @PathVariable String id,
            @RequestBody @Valid NoteRequest request) {
        return ResponseEntity.ok(noteService.updateNote(id, request));
    }

    @Operation(summary = "Delete a note", description = "Deletes an existing note")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Note deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Note not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNote(@Parameter(description = "ID of the note") @PathVariable String id) {
        noteService.deleteNote(id);
        return ResponseEntity.noContent().build();
    }
}
