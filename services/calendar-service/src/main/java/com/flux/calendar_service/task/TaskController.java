package com.flux.calendar_service.task;

import com.flux.calendar_service.task.dto.TaskRequest;
import com.flux.calendar_service.task.dto.TaskResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Endpoints for managing event tasks")
public class TaskController {
    private final TaskService taskService;

    @Operation(summary = "Create a task for an event", description = "Creates a new task associated with a specific event")
    @ApiResponse(responseCode = "200", description = "Task created successfully")
    @PostMapping("/event/{eventId}")
    public ResponseEntity<String> createTask(
            @Parameter(description = "ID of the event") @PathVariable String eventId,
            @RequestBody String name) {
        return ResponseEntity.ok(taskService.createTask(eventId, name));
    }

    @Operation(summary = "Get tasks by event ID", description = "Retrieves all tasks associated with a specific event")
    @GetMapping("/event/{eventId}")
    public ResponseEntity<java.util.List<TaskResponse>> getTasksByEventId(
            @Parameter(description = "ID of the event") @PathVariable String eventId) {
        return ResponseEntity.ok(taskService.getAllTasksByEventId(eventId));
    }

    @Operation(summary = "Get task by ID", description = "Retrieves detailed information about a specific task")
    @ApiResponse(responseCode = "200", description = "Task found")
    @ApiResponse(responseCode = "404", description = "Task not found")
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(
            @Parameter(description = "ID of the task") @PathVariable String id) {
        return ResponseEntity.ok(taskService.getById(id));
    }

    @Operation(summary = "Toggle task status", description = "Toggles the 'done' status of a specific task")
    @ApiResponse(responseCode = "202", description = "Task status toggled successfully")
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<?> toggleTask(@Parameter(description = "ID of the task") @PathVariable String id) {
        TaskResponse task = taskService.getById(id);
        if (task.isDone()) {
            taskService.incompleteTask(id);
        } else {
            taskService.completeTask(id);
        }
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .build();
    }

    @Operation(summary = "Update a task", description = "Updates the details of an existing task")
    @ApiResponse(responseCode = "200", description = "Task updated successfully")
    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(
            @Parameter(description = "ID of the task") @PathVariable String id,
            @RequestBody @Valid TaskRequest request) {
        return ResponseEntity.ok(taskService.updateTask(id, request));
    }

    @Operation(summary = "Delete a task", description = "Deletes an existing task")
    @ApiResponse(responseCode = "204", description = "Task deleted successfully")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@Parameter(description = "ID of the task") @PathVariable String id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
