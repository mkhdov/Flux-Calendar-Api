package com.flux.calendar_service.task;

import com.flux.calendar_service.calendar.Calendar;
import com.flux.calendar_service.note.NoteMapper;
import com.flux.calendar_service.subtask.SubtaskMapper;
import com.flux.calendar_service.task.dto.TaskRequest;
import com.flux.calendar_service.task.dto.TaskResponse;
import com.flux.calendar_service.task.dto.TaskUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskMapper {
    private final SubtaskMapper subtaskMapper;
    private final NoteMapper noteMapper;

    public TaskResponse toTaskResponse(Task task) {
        if (task == null) {
            return null;
        }

        return TaskResponse.builder()
                .id(task.getId())
                .calendarId(task.getCalendar() != null ? task.getCalendar().getId() : null)
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(task.getPriority())
                .date(task.getDate())
                .deadline(task.getDeadline())
                .subtasks(task.getSubtasks() != null
                        ? task.getSubtasks().stream().map(subtaskMapper::toSubtaskResponse).collect(Collectors.toList())
                        : Collections.emptyList())
                .notes(task.getNotes() != null
                        ? task.getNotes().stream().map(noteMapper::toNoteResponse).collect(Collectors.toList())
                        : Collections.emptyList())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    public Task toTask(TaskRequest request, Calendar calendar) {
        if (request == null) {
            return null;
        }

        Task task = Task.builder()
                .calendar(calendar)
                .title(request.title())
                .description(request.description())
                .status(request.status())
                .priority(request.priority())
                .date(request.date())
                .deadline(request.deadline())
                .build();

        if (request.subtasks() != null) {
            task.setSubtasks(request.subtasks().stream()
                    .map(s -> subtaskMapper.toSubtask(s, task))
                    .collect(Collectors.toList()));
        }

        return task;
    }

    public void updateTaskFromRequest(TaskUpdateRequest request, Task task) {
        if (request == null) {
            return;
        }

        if (request.title() != null)
            task.setTitle(request.title());
        if (request.description() != null)
            task.setDescription(request.description());
        if (request.status() != null)
            task.setStatus(request.status());
        if (request.priority() != null)
            task.setPriority(request.priority());
        if (request.date() != null)
            task.setDate(request.date());
        if (request.deadline() != null)
            task.setDeadline(request.deadline());
    }
}
