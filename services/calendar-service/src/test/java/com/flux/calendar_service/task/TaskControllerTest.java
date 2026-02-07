package com.flux.calendar_service.task;

import com.flux.calendar_service.task.dto.TaskRequest;
import com.flux.calendar_service.task.dto.TaskResponse;
import com.flux.calendar_service.task.dto.TaskUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createTask_ShouldReturnCreatedTask() throws Exception {
        TaskRequest request = TaskRequest.builder()
                .calendarId("cal1")
                .title("New Task")
                .status(TaskStatus.TODO)
                .build();

        TaskResponse response = TaskResponse.builder()
                .id("1")
                .calendarId("cal1")
                .title("New Task")
                .status(TaskStatus.TODO)
                .build();

        when(taskService.createTask(any(TaskRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.title").value("New Task"));
    }

    @Test
    void getTask_ShouldReturnTask() throws Exception {
        TaskResponse response = TaskResponse.builder()
                .id("1")
                .title("Existing Task")
                .build();

        when(taskService.getTask("1")).thenReturn(response);

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.title").value("Existing Task"));
    }

    @Test
    void getAllTasks_ShouldReturnListOfTasks() throws Exception {
        TaskResponse response = TaskResponse.builder()
                .id("1")
                .title("Task 1")
                .build();

        when(taskService.getAllTasks("cal1")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/tasks")
                .param("calendarId", "cal1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].title").value("Task 1"));
    }

    @Test
    void updateTask_ShouldReturnUpdatedTask() throws Exception {
        TaskUpdateRequest request = TaskUpdateRequest.builder()
                .title("Updated Task")
                .build();

        TaskResponse response = TaskResponse.builder()
                .id("1")
                .title("Updated Task")
                .build();

        when(taskService.updateTask(eq("1"), any(TaskUpdateRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/tasks/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Task"));
    }

    @Test
    void deleteTask_ShouldReturnNoContent() throws Exception {
        doNothing().when(taskService).deleteTask("1");

        mockMvc.perform(delete("/api/tasks/1"))
                .andExpect(status().isNoContent());
    }
}
