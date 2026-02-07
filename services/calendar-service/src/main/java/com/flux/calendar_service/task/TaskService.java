package com.flux.calendar_service.task;

import com.flux.calendar_service.calendar.Calendar;
import com.flux.calendar_service.calendar.CalendarRepository;
import com.flux.calendar_service.exception.ResourceNotFoundException;
import com.flux.calendar_service.task.dto.TaskRequest;
import com.flux.calendar_service.task.dto.TaskResponse;
import com.flux.calendar_service.task.dto.TaskUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;
    private final CalendarRepository calendarRepository;
    private final TaskMapper taskMapper;

    @Transactional
    public TaskResponse createTask(TaskRequest request) {
        Calendar calendar = calendarRepository.findById(request.calendarId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Calendar not found with ID: " + request.calendarId()));

        Task task = taskMapper.toTask(request, calendar);
        if (task.getSubtasks() != null) {
            task.getSubtasks().forEach(subtask -> subtask.setTask(task));
        }
        Task savedTask = taskRepository.save(task);

        return taskMapper.toTaskResponse(savedTask);
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(String id) {
        return taskRepository.findById(id)
                .map(taskMapper::toTaskResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getAllTasks(String calendarId) {
        return taskRepository.findAllByCalendarId(calendarId).stream()
                .map(taskMapper::toTaskResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public TaskResponse updateTask(String id, TaskUpdateRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with ID: " + id));

        taskMapper.updateTaskFromRequest(request, task);
        Task updatedTask = taskRepository.save(task);

        return taskMapper.toTaskResponse(updatedTask);
    }

    @Transactional
    public void deleteTask(String id) {
        if (!taskRepository.existsById(id)) {
            throw new ResourceNotFoundException("Task not found with ID: " + id);
        }
        taskRepository.deleteById(id);
    }
}
