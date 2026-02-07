package com.flux.calendar_service.event;

import com.flux.calendar_service.event.dto.EventRequest;
import com.flux.calendar_service.event.dto.EventResponse;
import com.flux.calendar_service.event.dto.EventUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventController.class)
class EventControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private EventService eventService;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        void createEvent_ShouldReturnId() throws Exception {
                String calendarId = "cal1";
                EventRequest request = new EventRequest(
                                "New Event", "Description", "#000000", EventType.MEETING, null,
                                LocalDateTime.now(), LocalDateTime.now().plusHours(1), false, EventStatus.NOTSTARTED,
                                Collections.emptyList(), Collections.emptyList());

                when(eventService.createEvent(eq(calendarId), any(EventRequest.class))).thenReturn("event1");

                mockMvc.perform(post("/events/calendar/{calendarId}", calendarId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(content().string("event1"));
        }

        @Test
        void getEventsByDateRange_ShouldReturnList() throws Exception {
                EventResponse response = new EventResponse(
                                "1", null, "Event 1", "Desc", "#000", null, null,
                                EventType.MEETING, LocalDateTime.now(), LocalDateTime.now().plusHours(1),
                                false, SyncStatus.SYNCED, EventStatus.NOTSTARTED,
                                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                                Collections.emptyList(),
                                LocalDateTime.now(), LocalDateTime.now());

                when(eventService.getEventsByDateRange(any(), any())).thenReturn(List.of(response));

                mockMvc.perform(get("/events/range")
                                .param("start", LocalDateTime.now().toString())
                                .param("end", LocalDateTime.now().plusDays(1).toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value("1"));
        }

        @Test
        void getEventById_ShouldReturnEvent() throws Exception {
                EventResponse response = new EventResponse(
                                "1", null, "Event 1", "Desc", "#000", null, null,
                                EventType.MEETING, LocalDateTime.now(), LocalDateTime.now().plusHours(1),
                                false, SyncStatus.SYNCED, EventStatus.NOTSTARTED,
                                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                                Collections.emptyList(),
                                LocalDateTime.now(), LocalDateTime.now());
                when(eventService.getEventById("1")).thenReturn(response);

                mockMvc.perform(get("/events/1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value("1"));
        }

        @Test
        void updateEvent_ShouldReturnAccepted() throws Exception {
                EventUpdateRequest request = new EventUpdateRequest(
                                "Updated", "Desc", "#000", null, null, null, null, null, null, null, null);
                doNothing().when(eventService).updateEvent(eq("1"), any(EventUpdateRequest.class));

                mockMvc.perform(put("/events/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isAccepted());
        }

        @Test
        void deleteEvent_ShouldReturnAccepted() throws Exception {
                doNothing().when(eventService).deleteEvent("1");

                mockMvc.perform(delete("/events/1"))
                                .andExpect(status().isAccepted());
        }
}
