package com.flux.calendar_service.task;

import com.flux.calendar_service.task.dto.TaskRequest;
import com.flux.calendar_service.task.dto.TaskResponse;
import com.flux.calendar_service.task.dto.TaskUpdateRequest;
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
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Endpoints for managing calendar tasks")
public class TaskController {
    private final TaskService taskService;

    @Operation(summary = "Create a new task", description = "Creates a new task for a specific calendar")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Task created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g. missing calendar ID or title)"),
            @ApiResponse(responseCode = "404", description = "Calendar not found")
    })
    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.createTask(request));
    }

    @Operation(summary = "Get task by ID", description = "Retrieves detailed information about a specific task")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Task found"),
            @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTask(
            @Parameter(description = "ID of the task to retrieve") @PathVariable String id) {
        return ResponseEntity.ok(taskService.getTask(id));
    }

    @Operation(summary = "Get all tasks by calendar", description = "Retrieves all tasks associated with a specific calendar")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of tasks (may be empty)"),
            @ApiResponse(responseCode = "404", description = "Calendar not found")
    })
    @GetMapping
    public ResponseEntity<List<TaskResponse>> getAllTasks(
            @Parameter(description = "ID of the calendar") @RequestParam String calendarId) {
        return ResponseEntity.ok(taskService.getAllTasks(calendarId));
    }

    @Operation(summary = "Update a task", description = "Updates the details of an existing task (partial update)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Task updated successfully"),
            @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(
            @Parameter(description = "ID of the task to update") @PathVariable String id,
            @RequestBody TaskUpdateRequest request) {
        return ResponseEntity.ok(taskService.updateTask(id, request));
    }

    @Operation(summary = "Delete a task", description = "Permanently deletes an existing task")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Task deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(
            @Parameter(description = "ID of the task to delete") @PathVariable String id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
