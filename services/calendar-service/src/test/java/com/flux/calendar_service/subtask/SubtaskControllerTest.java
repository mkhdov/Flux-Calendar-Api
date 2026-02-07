package com.flux.calendar_service.subtask;

import com.flux.calendar_service.subtask.dto.SubtaskRequest;
import com.flux.calendar_service.subtask.dto.SubtaskResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubtaskController.class)
class SubtaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubtaskService subtaskService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createSubtask_ShouldReturnId() throws Exception {
        String eventId = "event1";
        String description = "New Subtask";

        when(subtaskService.createSubtask(eq(eventId), any(String.class))).thenReturn("sub1");

        mockMvc.perform(post("/subtasks/event/{eventId}", eventId)
                .contentType(MediaType.TEXT_PLAIN) // Controller expects body as string? let's check
                .content(description))
                .andExpect(status().isOk())
                .andExpect(content().string("sub1"));
    }

    @Test
    void getSubtasksByEventId_ShouldReturnList() throws Exception {
        SubtaskResponse response = new SubtaskResponse("1", "Subtask 1", false);
        when(subtaskService.getAllSubtasksByEventId("event1")).thenReturn(List.of(response));

        mockMvc.perform(get("/subtasks/event/event1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"));
    }

    @Test
    void getSubtaskById_ShouldReturnSubtask() throws Exception {
        SubtaskResponse response = new SubtaskResponse("1", "Subtask 1", false);
        when(subtaskService.getById("1")).thenReturn(response);

        mockMvc.perform(get("/subtasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"));
    }

    @Test
    void toggleSubtask_ShouldReturnAccepted() throws Exception {
        SubtaskResponse response = new SubtaskResponse("1", "Subtask 1", false);
        when(subtaskService.getById("1")).thenReturn(response);
        doNothing().when(subtaskService).completeSubtask("1");

        mockMvc.perform(patch("/subtasks/1/toggle"))
                .andExpect(status().isAccepted());
    }

    @Test
    void updateSubtask_ShouldReturnUpdated() throws Exception {
        SubtaskRequest request = new SubtaskRequest("Updated", true);
        SubtaskResponse response = new SubtaskResponse("1", "Updated", true);

        when(subtaskService.updateSubtask(eq("1"), any(SubtaskRequest.class))).thenReturn(response);

        mockMvc.perform(put("/subtasks/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtask").value("Updated"))
                .andExpect(jsonPath("$.isDone").value(true));
    }

    @Test
    void deleteSubtask_ShouldReturnNoContent() throws Exception {
        doNothing().when(subtaskService).deleteSubtask("1");

        mockMvc.perform(delete("/subtasks/1"))
                .andExpect(status().isNoContent());
    }
}
