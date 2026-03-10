package com.flux.calendar_service.calendar;

import com.flux.calendar_service.calendar.dto.CalendarRequest;
import com.flux.calendar_service.calendar.dto.CalendarResponse;
import com.flux.calendar_service.calendar.dto.CalendarUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CalendarController.class)
class CalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CalendarService calendarService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void create_ShouldReturnId() throws Exception {
        CalendarRequest request = new CalendarRequest(
                "user1", "My Calendar", "Description", "#000", "UTC", true);

        when(calendarService.createCalendar(any(CalendarRequest.class))).thenReturn("cal1");

        mockMvc.perform(post("/calendars")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().string("cal1"));
    }

    @Test
    void getById_ShouldReturnCalendar() throws Exception {
        CalendarResponse response = new CalendarResponse(
                "1", "user1", "My Cal", "Desc", "#000", "UTC", true, false,
                Instant.now(), Instant.now());

        when(calendarService.getCalendarById("1")).thenReturn(response);

        mockMvc.perform(get("/calendars/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"));
    }

    @Test
    void getByUserId_ShouldReturnList() throws Exception {
        CalendarResponse response = new CalendarResponse(
                "1", "user1", "My Cal", "Desc", "#000", "UTC", true, false,
                Instant.now(), Instant.now());

        when(calendarService.getCalendarsByUserId("user1")).thenReturn(List.of(response));

        mockMvc.perform(get("/calendars/user/id/user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"));
    }

    @Test
    void update_ShouldReturnAccepted() throws Exception {
        CalendarUpdateRequest request = new CalendarUpdateRequest(
                "Updated", "Desc", "#111", "UTC");

        doNothing().when(calendarService).updateCalendar(eq("1"), any(CalendarUpdateRequest.class));

        mockMvc.perform(put("/calendars/update/1")
                .contentType(MediaType.APPLICATION_JSON)
                // Note: CalendarUpdateRequest might be a record too
                .content("{\"title\":\"Updated\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void deleteCalendar_ShouldReturnAccepted() throws Exception {
        doNothing().when(calendarService).deleteCalendar("1");

        mockMvc.perform(delete("/calendars/1"))
                .andExpect(status().isAccepted());
    }
}
