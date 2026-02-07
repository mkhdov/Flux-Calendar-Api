package com.flux.calendar_service.task;

import com.flux.calendar_service.calendar.Calendar;
import com.flux.calendar_service.calendar.CalendarRepository;
import com.flux.calendar_service.exception.ResourceNotFoundException;
import com.flux.calendar_service.task.dto.TaskRequest;
import com.flux.calendar_service.task.dto.TaskResponse;
import com.flux.calendar_service.task.dto.TaskUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private CalendarRepository calendarRepository;

    @Mock
    private TaskMapper taskMapper;

    @InjectMocks
    private TaskService taskService;

    @Test
    void createTask_ShouldReturnResponse() {
        TaskRequest request = TaskRequest.builder()
                .calendarId("cal1")
                .title("New Task")
                .build();
        Calendar calendar = new Calendar();
        calendar.setId("cal1");
        Task task = new Task();
        Task savedTask = new Task();
        savedTask.setId("1");
        TaskResponse expectedResponse = TaskResponse.builder().id("1").build();

        when(calendarRepository.findById("cal1")).thenReturn(Optional.of(calendar));
        when(taskMapper.toTask(request, calendar)).thenReturn(task);
        when(taskRepository.save(task)).thenReturn(savedTask);
        when(taskMapper.toTaskResponse(savedTask)).thenReturn(expectedResponse);

        TaskResponse response = taskService.createTask(request);

        assertNotNull(response);
        assertEquals("1", response.id());
        verify(taskRepository).save(task);
    }

    @Test
    void getTask_ShouldReturnResponse() {
        String id = "1";
        Task task = new Task();
        TaskResponse expectedResponse = TaskResponse.builder().id("1").build();

        when(taskRepository.findById(id)).thenReturn(Optional.of(task));
        when(taskMapper.toTaskResponse(task)).thenReturn(expectedResponse);

        TaskResponse response = taskService.getTask(id);

        assertNotNull(response);
        assertEquals("1", response.id());
    }

    @Test
    void getTask_ShouldThrowException_WhenNotFound() {
        String id = "1";
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> taskService.getTask(id));
    }

    @Test
    void getAllTasks_ShouldReturnList() {
        String calendarId = "cal1";
        Task task = new Task();
        TaskResponse expectedResponse = TaskResponse.builder().id("1").build();

        when(taskRepository.findAllByCalendarId(calendarId)).thenReturn(List.of(task));
        when(taskMapper.toTaskResponse(task)).thenReturn(expectedResponse);

        List<TaskResponse> responses = taskService.getAllTasks(calendarId);

        assertNotNull(responses);
        assertEquals(1, responses.size());
    }

    @Test
    void updateTask_ShouldReturnResponse() {
        String id = "1";
        TaskUpdateRequest request = TaskUpdateRequest.builder().title("Updated").build();
        Task task = new Task();
        Task updatedTask = new Task();
        TaskResponse expectedResponse = TaskResponse.builder().id("1").title("Updated").build();

        when(taskRepository.findById(id)).thenReturn(Optional.of(task));
        doNothing().when(taskMapper).updateTaskFromRequest(request, task);
        when(taskRepository.save(task)).thenReturn(updatedTask);
        when(taskMapper.toTaskResponse(updatedTask)).thenReturn(expectedResponse);

        TaskResponse response = taskService.updateTask(id, request);

        assertNotNull(response);
        assertEquals("Updated", response.title());
    }

    @Test
    void deleteTask_ShouldDelete_WhenExists() {
        String id = "1";
        when(taskRepository.existsById(id)).thenReturn(true);
        doNothing().when(taskRepository).deleteById(id);

        taskService.deleteTask(id);

        verify(taskRepository).deleteById(id);
    }

    @Test
    void deleteTask_ShouldThrow_WhenNotExists() {
        String id = "1";
        when(taskRepository.existsById(id)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> taskService.deleteTask(id));
    }
}
