package com.flux.calendar_service.subtask;

import com.flux.calendar_service.event.Event;
import com.flux.calendar_service.task.Task;
import com.flux.calendar_service.subtask.dto.SubtaskRequest;
import com.flux.calendar_service.subtask.dto.SubtaskResponse;
import org.springframework.stereotype.Service;

@Service
public class SubtaskMapper {

    public SubtaskResponse toSubtaskResponse(Subtask subtask) {
        if (subtask == null) {
            return null;
        }
        return new SubtaskResponse(
                subtask.getId(),
                subtask.getSubtask(),
                subtask.isDone());
    }

    public Subtask toSubtask(SubtaskRequest request, Event event) {
        if (request == null) {
            return null;
        }
        return Subtask.builder()
                .subtask(request.subtask())
                .isDone(request.isDone())
                .event(event)
                .build();
    }

    public Subtask toSubtask(SubtaskRequest request, Task task) {
        if (request == null) {
            return null;
        }
        return Subtask.builder()
                .subtask(request.subtask())
                .isDone(request.isDone())
                .task(task)
                .build();
    }
}
