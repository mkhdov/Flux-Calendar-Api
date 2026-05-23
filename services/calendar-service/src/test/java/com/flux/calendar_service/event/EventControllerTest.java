package com.flux.calendar_service.event;

import com.flux.calendar_service.event.dto.EventRequest;
import com.flux.calendar_service.event.dto.EventListItemResponse;
import com.flux.calendar_service.event.dto.EventResponse;
import com.flux.calendar_service.event.dto.EventUpdateRequest;
import com.flux.calendar_service.event.dto.EventMonthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
                Instant now = Instant.now();
                EventRequest request = new EventRequest(
                                "New Event", "Description", "#000000", EventType.MEETING, null,
                                now, now.plusSeconds(3600), false, EventStatus.NOTSTARTED,
                                Collections.emptyList(), Collections.emptyList(), null, null, null);

                when(eventService.createEvent(eq(calendarId), any(EventRequest.class))).thenReturn(List.of("event1"));

                mockMvc.perform(post("/events/calendar/{calendarId}", calendarId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$[0]").value("event1"));
        }

        @Test
        void getEventsByDateRange_ShouldReturnList() throws Exception {
                Instant now = Instant.now();

                EventListItemResponse response = new EventListItemResponse(
                        "1", "cal1", "Event 1", "Desc", "#000",
                        now, now.plusSeconds(3600),
                        false, EventStatus.NOTSTARTED,
                        null, false, false, false, false, false, false, false
                );

                when(eventService.getEventsByDateRange(any(), any(), any())).thenReturn(List.of(response));

                mockMvc.perform(get("/events/range")
                                .param("calendarId", "1")
                                .param("start", now.toString())
                                .param("end", now.plusSeconds(86400).toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value("1"));
        }

        @Test
        void getEventsByDateRange_WithInvalidStart_ShouldReturnBadRequest() throws Exception {
                Instant now = Instant.now();
                mockMvc.perform(get("/events/range")
                                .param("calendarId", "1")
                                .param("start", "NaN-NaN-NaNTNaN:NaN:NaN")
                                .param("end", now.plusSeconds(86400).toString()))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errors.start").exists());
        }

        @Test
        void getEventsByDateRange_ExceedingSevenDays_ShouldReturnBadRequest() throws Exception {
                Instant start = Instant.now();
                Instant end = start.plus(10, ChronoUnit.DAYS);

                when(eventService.getEventsByDateRange(any(), any(), any()))
                                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Week view supports max 7 days"));

                mockMvc.perform(get("/events/range")
                                .param("calendarId", "1")
                                .param("start", start.toString())
                                .param("end", end.toString()))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void getEventsByDateRange_ZeroDayRange_ShouldReturnBadRequest() throws Exception {
                Instant start = Instant.now();
                Instant end = start.plusSeconds(30);

                when(eventService.getEventsByDateRange(any(), any(), any()))
                                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date range must be at least 1 day"));

                mockMvc.perform(get("/events/range")
                                .param("calendarId", "1")
                                .param("start", start.toString())
                                .param("end", end.toString()))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void getEventsByMonth_ShouldReturnList() throws Exception {
                Instant now = Instant.now();
                EventMonthResponse response = new EventMonthResponse(
                                "1", "cal1", "Event 1", "#000", now, now.plusSeconds(3600), false, null, false);

                when(eventService.getEventsByMonth(eq("1"), eq(2023), eq(10))).thenReturn(List.of(response));

                mockMvc.perform(get("/events/month")
                                .param("calendarId", "1")
                                .param("year", "2023")
                                .param("month", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value("1"))
                                .andExpect(jsonPath("$[0].title").value("Event 1"));
        }

        @Test
        void getEventsByMonth_WithInvalidMonth_ShouldReturnBadRequest() throws Exception {
                when(eventService.getEventsByMonth(eq("1"), eq(2023), eq(13)))
                                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month must be between 1 and 12"));

                mockMvc.perform(get("/events/month")
                                .param("calendarId", "1")
                                .param("year", "2023")
                                .param("month", "13"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void getEventById_ShouldReturnEvent() throws Exception {
                Instant now = Instant.now();
                EventResponse response = new EventResponse(
                                "1", null, "Event 1", "Desc", "#000", null, null,
                                EventType.MEETING, now, now.plusSeconds(3600),
                                false, SyncStatus.SYNCED, EventStatus.NOTSTARTED,
                                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                                Collections.emptyList(),
                                now, now,
                                null, false);
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

