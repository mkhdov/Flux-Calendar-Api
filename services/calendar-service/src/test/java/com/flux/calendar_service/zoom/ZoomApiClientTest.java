package com.flux.calendar_service.zoom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flux.calendar_service.zoom.dto.ZoomMeetingRequest;
import com.flux.calendar_service.zoom.dto.ZoomMeetingResponse;
import com.flux.calendar_service.zoom.dto.ZoomTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZoomApiClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ZoomApiClient zoomApiClient;

    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String REDIRECT_URI = "http://localhost:8083/zoom/callback";
    private static final String BASE_URL = "https://api.zoom.us/v2";
    private static final String TOKEN_URL = "https://zoom.us/oauth/token";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(zoomApiClient, "clientId", CLIENT_ID);
        ReflectionTestUtils.setField(zoomApiClient, "clientSecret", CLIENT_SECRET);
        ReflectionTestUtils.setField(zoomApiClient, "redirectUri", REDIRECT_URI);
        ReflectionTestUtils.setField(zoomApiClient, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(zoomApiClient, "tokenUrl", TOKEN_URL);
        ReflectionTestUtils.setField(zoomApiClient, "restTemplate", restTemplate);
    }

    @Test
    void getAccessToken_ShouldReturnToken_WhenSuccessful() {
        // Given
        String authCode = "test-auth-code";
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("access_token", "test-access-token");
        responseBody.put("token_type", "Bearer");
        responseBody.put("refresh_token", "test-refresh-token");
        responseBody.put("expires_in", 3600);
        responseBody.put("scope", "meeting:write");

        ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.postForEntity(eq(TOKEN_URL), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        // When
        ZoomTokenResponse result = zoomApiClient.getAccessToken(authCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo("test-access-token");
        assertThat(result.getRefreshToken()).isEqualTo("test-refresh-token");
        assertThat(result.getExpiresIn()).isEqualTo(3600L);
        verify(restTemplate).postForEntity(eq(TOKEN_URL), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void getAccessToken_ShouldThrowException_WhenFails() {
        // Given
        String authCode = "test-auth-code";
        when(restTemplate.postForEntity(eq(TOKEN_URL), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("API Error"));

        // When & Then
        assertThatThrownBy(() -> zoomApiClient.getAccessToken(authCode))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to get Zoom access token");
    }

    @Test
    void refreshToken_ShouldReturnNewToken_WhenSuccessful() {
        // Given
        String refreshToken = "test-refresh-token";
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("access_token", "new-access-token");
        responseBody.put("token_type", "Bearer");
        responseBody.put("refresh_token", "new-refresh-token");
        responseBody.put("expires_in", 3600);
        responseBody.put("scope", "meeting:write");

        ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.postForEntity(eq(TOKEN_URL), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        // When
        ZoomTokenResponse result = zoomApiClient.refreshToken(refreshToken);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo("new-access-token");
        assertThat(result.getRefreshToken()).isEqualTo("new-refresh-token");
    }

    @Test
    void createMeeting_ShouldReturnMeetingResponse_WhenSuccessful() {
        // Given
        String accessToken = "test-access-token";
        ZoomMeetingRequest request = ZoomMeetingRequest.builder()
                .topic("Test Meeting")
                .type(2)
                .duration(60)
                .build();

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", 123456789L);
        responseBody.put("uuid", "test-uuid");
        responseBody.put("host_id", "test-host-id");
        responseBody.put("host_email", "host@example.com");
        responseBody.put("topic", "Test Meeting");
        responseBody.put("type", 2);
        responseBody.put("duration", 60);
        responseBody.put("join_url", "https://zoom.us/j/123456789");
        responseBody.put("start_url", "https://zoom.us/s/123456789");
        responseBody.put("password", "test123");

        ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.CREATED);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        // When
        ZoomMeetingResponse result = zoomApiClient.createMeeting(accessToken, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(123456789L);
        assertThat(result.getTopic()).isEqualTo("Test Meeting");
        assertThat(result.getJoinUrl()).isEqualTo("https://zoom.us/j/123456789");
        verify(restTemplate).postForEntity(contains("/users/me/meetings"), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void createMeeting_ShouldThrowException_WhenApiFails() {
        // Given
        String accessToken = "test-access-token";
        ZoomMeetingRequest request = ZoomMeetingRequest.builder()
                .topic("Test Meeting")
                .build();

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST,
                        "Bad Request",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        null));

        // When & Then
        assertThatThrownBy(() -> zoomApiClient.createMeeting(accessToken, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to create Zoom meeting");
    }

    @Test
    void updateMeeting_ShouldCallApiSuccessfully() {
        // Given
        String accessToken = "test-access-token";
        String meetingId = "123456789";
        ZoomMeetingRequest request = ZoomMeetingRequest.builder()
                .topic("Updated Meeting")
                .build();

        when(restTemplate.exchange(anyString(), eq(HttpMethod.PATCH), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.NO_CONTENT));

        // When
        zoomApiClient.updateMeeting(accessToken, meetingId, request);

        // Then
        verify(restTemplate).exchange(
                contains("/meetings/" + meetingId),
                eq(HttpMethod.PATCH),
                any(HttpEntity.class),
                eq(Void.class));
    }

    @Test
    void deleteMeeting_ShouldCallApiSuccessfully() {
        // Given
        String accessToken = "test-access-token";
        String meetingId = "123456789";

        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.NO_CONTENT));

        // When
        zoomApiClient.deleteMeeting(accessToken, meetingId);

        // Then
        verify(restTemplate).exchange(
                contains("/meetings/" + meetingId),
                eq(HttpMethod.DELETE),
                any(HttpEntity.class),
                eq(Void.class));
    }

    @Test
    void getUserInfo_ShouldReturnUserInfo_WhenSuccessful() {
        // Given
        String accessToken = "test-access-token";
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", "user-123");
        responseBody.put("email", "user@example.com");
        responseBody.put("first_name", "Test");
        responseBody.put("last_name", "User");

        ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        // When
        Map<String, Object> result = zoomApiClient.getUserInfo(accessToken);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("email")).isEqualTo("user@example.com");
        verify(restTemplate).exchange(
                contains("/users/me"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class));
    }
}
