package com.flux.calendar_service.note;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flux.calendar_service.exception.ResourceNotFoundException;
import com.flux.calendar_service.note.dto.NoteRequest;
import com.flux.calendar_service.note.dto.NoteResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NoteController.class)
class NoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NoteService noteService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final LocalDateTime NOW = LocalDateTime.now();

    @Test
    void createNoteForEvent_ShouldReturnNoteResponse() throws Exception {
        String eventId = "event1";
        NoteRequest request = new NoteRequest("Event note content");
        NoteResponse response = new NoteResponse("note1", request.content(), NOW, NOW);

        when(noteService.createNoteForEvent(eq(eventId), any(NoteRequest.class))).thenReturn(response);

        mockMvc.perform(post("/notes/event/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("note1"))
                .andExpect(jsonPath("$.content").value("Event note content"));
    }

    @Test
    void createNoteForEvent_WhenEventNotFound_ShouldReturnNotFound() throws Exception {
        String eventId = "missing";
        NoteRequest request = new NoteRequest("Content");

        when(noteService.createNoteForEvent(eq(eventId), any(NoteRequest.class)))
                .thenThrow(new ResourceNotFoundException("Event not found with ID: " + eventId));

        mockMvc.perform(post("/notes/event/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createNoteForTask_ShouldReturnNoteResponse() throws Exception {
        String taskId = "task1";
        NoteRequest request = new NoteRequest("Task note content");
        NoteResponse response = new NoteResponse("note2", request.content(), NOW, NOW);

        when(noteService.createNoteForTask(eq(taskId), any(NoteRequest.class))).thenReturn(response);

        mockMvc.perform(post("/notes/task/{taskId}", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("note2"))
                .andExpect(jsonPath("$.content").value("Task note content"));
    }

    @Test
    void getNotesByEventId_ShouldReturnList() throws Exception {
        NoteResponse response = new NoteResponse("1", "Note 1", NOW, NOW);
        when(noteService.getNotesByEventId("event1")).thenReturn(List.of(response));

        mockMvc.perform(get("/notes/event/event1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].content").value("Note 1"));
    }

    @Test
    void getNotesByTaskId_ShouldReturnList() throws Exception {
        NoteResponse response = new NoteResponse("2", "Task note", NOW, NOW);
        when(noteService.getNotesByTaskId("task1")).thenReturn(List.of(response));

        mockMvc.perform(get("/notes/task/task1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("2"))
                .andExpect(jsonPath("$[0].content").value("Task note"));
    }

    @Test
    void getNoteById_ShouldReturnNote() throws Exception {
        NoteResponse response = new NoteResponse("note1", "My note", NOW, NOW);
        when(noteService.getById("note1")).thenReturn(response);

        mockMvc.perform(get("/notes/note1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("note1"))
                .andExpect(jsonPath("$.content").value("My note"));
    }

    @Test
    void getNoteById_WhenNotFound_ShouldReturnNotFound() throws Exception {
        when(noteService.getById("missing")).thenThrow(new ResourceNotFoundException("Note not found with ID: missing"));

        mockMvc.perform(get("/notes/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateNote_ShouldReturnUpdatedNote() throws Exception {
        NoteRequest request = new NoteRequest("Updated content");
        NoteResponse response = new NoteResponse("note1", "Updated content", NOW, NOW);

        when(noteService.updateNote(eq("note1"), any(NoteRequest.class))).thenReturn(response);

        mockMvc.perform(put("/notes/note1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Updated content"));
    }

    @Test
    void updateNote_WhenNotFound_ShouldReturnNotFound() throws Exception {
        NoteRequest request = new NoteRequest("Content");
        when(noteService.updateNote(eq("missing"), any(NoteRequest.class)))
                .thenThrow(new ResourceNotFoundException("Note not found with ID: missing"));

        mockMvc.perform(put("/notes/missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteNote_ShouldReturnNoContent() throws Exception {
        doNothing().when(noteService).deleteNote("note1");

        mockMvc.perform(delete("/notes/note1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteNote_WhenNotFound_ShouldReturnNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Note not found with ID: missing"))
                .when(noteService).deleteNote("missing");

        mockMvc.perform(delete("/notes/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createNoteForEvent_WithBlankContent_ShouldReturnBadRequest() throws Exception {
        NoteRequest request = new NoteRequest("   ");

        mockMvc.perform(post("/notes/event/event1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
