package com.flux.calendar_service.zoom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flux.calendar_service.calendar.Calendar;
import com.flux.calendar_service.conference.Conference;
import com.flux.calendar_service.conference.ConferenceRepository;
import com.flux.calendar_service.event.Event;
import com.flux.calendar_service.event.EventRepository;
import com.flux.calendar_service.event.EventStatus;
import com.flux.calendar_service.event.EventType;
import com.flux.calendar_service.exception.ZoomException;
import com.flux.calendar_service.zoom.dto.ZoomMeetingRequest;
import com.flux.calendar_service.zoom.dto.ZoomMeetingResponse;
import com.flux.calendar_service.zoom.dto.ZoomTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZoomMeetingServiceTest {

    @Mock
    private ZoomApiClient zoomApiClient;

    @Mock
    private ConferenceRepository conferenceRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ZoomMeetingService zoomMeetingService;

    private Event testEvent;
    private Conference testConference;
    private ZoomMeetingResponse zoomMeetingResponse;
    private static final String USER_ID = "user-123";
    private static final String EVENT_ID = "event-123";
    private static final String ACCESS_TOKEN = "test-access-token";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        Calendar calendar = Calendar.builder()
                .id("cal-123")
                .title("Test Calendar")
                .build();

        testEvent = Event.builder()
                .id(EVENT_ID)
                .calendar(calendar)
                .title("Test Meeting")
                .description("Test Description")
                .colorHex("#FF0000")
                .type(EventType.MEETING)
                .status(EventStatus.NOTSTARTED)
                .startTime(Instant.now().plusSeconds(86400))
                .endTime(Instant.now().plusSeconds(90000))
                .allDay(false)
                .build();

        testConference = Conference.builder()
                .id("conf-123")
                .type(Conference.ConferenceType.ZOOM)
                .zoomMeetingId("123456789")
                .conferenceLink("https://zoom.us/j/123456789")
                .startUrl("https://zoom.us/s/123456789")
                .conferencePassword("test123")
                .hostEmail("host@example.com")
                .duration(60)
                .timezone("UTC")
                .platformName("Zoom")
                .build();

        zoomMeetingResponse = ZoomMeetingResponse.builder()
                .id(123456789L)
                .uuid("test-uuid")
                .hostId("host-id")
                .hostEmail("host@example.com")
                .topic("Test Meeting")
                .type(2)
                .duration(60)
                .joinUrl("https://zoom.us/j/123456789")
                .startUrl("https://zoom.us/s/123456789")
                .password("test123")
                .settings(new HashMap<>())
                .build();
    }

    @Test
    void connectZoom_ShouldStoreTokens_WhenSuccessful() {
        // Given
        String authCode = "test-auth-code";
        ZoomTokenResponse tokenResponse = ZoomTokenResponse.builder()
                .accessToken(ACCESS_TOKEN)
                .refreshToken("refresh-token")
                .expiresIn(3600L)
                .build();

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("email", "user@example.com");

        when(zoomApiClient.getAccessToken(authCode)).thenReturn(tokenResponse);
        when(zoomApiClient.getUserInfo(ACCESS_TOKEN)).thenReturn(userInfo);

        // When
        Map<String, Object> result = zoomMeetingService.connectZoom(USER_ID, authCode);

        // Then
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("email")).isEqualTo("user@example.com");
        verify(valueOperations).set(
                eq("zoom:token:" + USER_ID),
                eq(ACCESS_TOKEN),
                any(Duration.class));
        verify(valueOperations).set(
                eq("zoom:refresh:" + USER_ID),
                eq("refresh-token"));
    }

    @Test
    void disconnectZoom_ShouldRemoveTokens() {
        // When
        zoomMeetingService.disconnectZoom(USER_ID);

        // Then
        verify(redisTemplate).delete("zoom:token:" + USER_ID);
        verify(redisTemplate).delete("zoom:refresh:" + USER_ID);
    }

    @Test
    void createZoomMeeting_ShouldCreateConference_WhenSuccessful() {
        // Given
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
        when(valueOperations.get("zoom:token:" + USER_ID)).thenReturn(ACCESS_TOKEN);
        when(zoomApiClient.createMeeting(eq(ACCESS_TOKEN), any(ZoomMeetingRequest.class)))
                .thenReturn(zoomMeetingResponse);
        when(conferenceRepository.save(any(Conference.class))).thenReturn(testConference);
        when(eventRepository.save(any(Event.class))).thenReturn(testEvent);

        // When
        Conference result = zoomMeetingService.createZoomMeeting(EVENT_ID, USER_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(Conference.ConferenceType.ZOOM);
        verify(eventRepository).findById(EVENT_ID);
        verify(zoomApiClient).createMeeting(eq(ACCESS_TOKEN), any(ZoomMeetingRequest.class));
        verify(conferenceRepository).save(any(Conference.class));
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    void createZoomMeeting_ShouldThrowException_WhenEventNotFound() {
        // Given
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> zoomMeetingService.createZoomMeeting(EVENT_ID, USER_ID))
                .isInstanceOf(ZoomException.class)
                .hasMessageContaining("Event not found");

        verify(zoomApiClient, never()).createMeeting(anyString(), any());
    }

    @Test
    void createZoomMeeting_ShouldThrowException_WhenNotConnected() {
        // Given
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
        when(valueOperations.get("zoom:token:" + USER_ID)).thenReturn(null);
        when(valueOperations.get("zoom:refresh:" + USER_ID)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> zoomMeetingService.createZoomMeeting(EVENT_ID, USER_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Zoom not connected");
    }

    @Test
    void updateZoomMeeting_ShouldUpdateConference_WhenSuccessful() {
        // Given
        testEvent.setConference(testConference);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
        when(valueOperations.get("zoom:token:" + USER_ID)).thenReturn(ACCESS_TOKEN);
        doNothing().when(zoomApiClient).updateMeeting(eq(ACCESS_TOKEN), anyString(), any(ZoomMeetingRequest.class));
        when(conferenceRepository.save(any(Conference.class))).thenReturn(testConference);

        // When
        Conference result = zoomMeetingService.updateZoomMeeting(EVENT_ID, USER_ID);

        // Then
        assertThat(result).isNotNull();
        verify(zoomApiClient).updateMeeting(eq(ACCESS_TOKEN), eq("123456789"), any(ZoomMeetingRequest.class));
        verify(conferenceRepository).save(any(Conference.class));
    }

    @Test
    void updateZoomMeeting_ShouldThrowException_WhenConferenceNotFound() {
        // Given
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));

        // When & Then
        assertThatThrownBy(() -> zoomMeetingService.updateZoomMeeting(EVENT_ID, USER_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Zoom meeting not found");
    }

    @Test
    void deleteZoomMeeting_ShouldDeleteConference_WhenSuccessful() {
        // Given
        testEvent.setConference(testConference);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
        when(valueOperations.get("zoom:token:" + USER_ID)).thenReturn(ACCESS_TOKEN);
        doNothing().when(zoomApiClient).deleteMeeting(ACCESS_TOKEN, "123456789");
        when(eventRepository.save(any(Event.class))).thenReturn(testEvent);

        // When
        zoomMeetingService.deleteZoomMeeting(EVENT_ID, USER_ID);

        // Then
        verify(zoomApiClient).deleteMeeting(ACCESS_TOKEN, "123456789");
        verify(conferenceRepository).delete(testConference);
        verify(eventRepository).save(testEvent);
    }

    @Test
    void isConnected_ShouldReturnTrue_WhenTokenExists() {
        // Given
        when(valueOperations.get("zoom:token:" + USER_ID)).thenReturn(ACCESS_TOKEN);

        // When
        boolean result = zoomMeetingService.isConnected(USER_ID);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isConnected_ShouldReturnFalse_WhenTokenDoesNotExist() {
        // Given
        when(valueOperations.get("zoom:token:" + USER_ID)).thenReturn(null);

        // When
        boolean result = zoomMeetingService.isConnected(USER_ID);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void getValidAccessToken_ShouldRefreshToken_WhenExpired() {
        // Given
        String refreshToken = "refresh-token";
        ZoomTokenResponse newTokenResponse = ZoomTokenResponse.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
                .expiresIn(3600L)
                .build();

        when(valueOperations.get("zoom:token:" + USER_ID)).thenReturn(null);
        when(valueOperations.get("zoom:refresh:" + USER_ID)).thenReturn(refreshToken);
        when(zoomApiClient.refreshToken(refreshToken)).thenReturn(newTokenResponse);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(testEvent));
        when(zoomApiClient.createMeeting(eq("new-access-token"), any(ZoomMeetingRequest.class)))
                .thenReturn(zoomMeetingResponse);
        when(conferenceRepository.save(any(Conference.class))).thenReturn(testConference);
        when(eventRepository.save(any(Event.class))).thenReturn(testEvent);

        // When
        zoomMeetingService.createZoomMeeting(EVENT_ID, USER_ID);

        // Then
        verify(zoomApiClient).refreshToken(refreshToken);
        verify(valueOperations).set(
                eq("zoom:token:" + USER_ID),
                eq("new-access-token"),
                any(Duration.class));
    }
}
