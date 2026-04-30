package com.flux.calendar_service.zoom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flux.calendar_service.conference.Conference;
import com.flux.calendar_service.conference.ConferenceRepository;
import com.flux.calendar_service.connects.ConnectService;
import com.flux.calendar_service.event.Event;
import com.flux.calendar_service.event.EventRepository;
import com.flux.calendar_service.exception.ResourceNotFoundException;
import com.flux.calendar_service.exception.ZoomException;
import com.flux.calendar_service.zoom.dto.ZoomMeetingRequest;
import com.flux.calendar_service.zoom.dto.ZoomMeetingResponse;
import com.flux.calendar_service.zoom.dto.ZoomTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZoomMeetingService {

    private final ZoomApiClient zoomApiClient;
    private final ConferenceRepository conferenceRepository;
    private final EventRepository eventRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConnectService connectService;

    private static final String ZOOM_TOKEN_KEY_PREFIX = "zoom:token:";
    private static final String ZOOM_REFRESH_TOKEN_KEY_PREFIX = "zoom:refresh:";

    /**
     * Connect Zoom account by exchanging authorization code for access token
     */
    @Transactional
    public Map<String, Object> connectZoom(String userId, String authorizationCode) {
        try {
            // Exchange authorization code for access token
            ZoomTokenResponse tokenResponse = zoomApiClient.getAccessToken(authorizationCode);

            // Store tokens in Redis
            storeTokens(userId, tokenResponse);

            // Get user info
            Map<String, Object> userInfo = zoomApiClient.getUserInfo(tokenResponse.getAccessToken());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("email", userInfo.get("email"));
            result.put("message", "Zoom connected successfully");
            //connect zoom mark
            connectService.connectZoom(userId);
            return result;
        } catch (Exception e) {
            log.error("Error connecting Zoom for user: {}", userId, e);
            throw new RuntimeException("Failed to connect Zoom: " + e.getMessage(), e);
        }
    }

    /**
     * Disconnect Zoom account
     */
    @Transactional
    public void disconnectZoom(String userId) {
        try {
            // Remove tokens from Redis
            redisTemplate.delete(ZOOM_TOKEN_KEY_PREFIX + userId);
            redisTemplate.delete(ZOOM_REFRESH_TOKEN_KEY_PREFIX + userId);
            connectService.disconnectZoom(userId);
            log.info("Zoom disconnected for user: {}", userId);
        } catch (Exception e) {
            log.error("Error disconnecting Zoom for user: {}", userId, e);
            throw new RuntimeException("Failed to disconnect Zoom: " + e.getMessage(), e);
        }
    }

    /**
     * Create a Zoom meeting for an event
     */
    @Transactional(noRollbackFor = ZoomException.class)
    public Conference createZoomMeeting(String eventId, String userId) {
        try {
            // Get event
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

            // Get access token
            String accessToken = getValidAccessToken(userId);

            // Create meeting request
            ZoomMeetingRequest meetingRequest = buildMeetingRequest(event);

            // Call Zoom API
            ZoomMeetingResponse zoomResponse = zoomApiClient.createMeeting(accessToken, meetingRequest);

            // Create conference entity
            Conference conference = Conference.builder()
                    .type(Conference.ConferenceType.ZOOM)
                    .zoomMeetingId(String.valueOf(zoomResponse.getId()))
                    .conferenceLink(zoomResponse.getJoinUrl())
                    .startUrl(zoomResponse.getStartUrl())
                    .conferencePassword(zoomResponse.getPassword())
                    .hostEmail(zoomResponse.getHostEmail())
                    .duration(zoomResponse.getDuration())
                    .timezone(zoomResponse.getTimezone())
                    .agenda(zoomResponse.getAgenda())
                    .platformName("Zoom")
                    .syncStatus(Conference.SyncStatus.SYNCED)
                    .lastSynced(Instant.now())
                    .build();

            // Store settings as JSON
            if (zoomResponse.getSettings() != null) {
                try {
                    conference.setSettings(objectMapper.writeValueAsString(zoomResponse.getSettings()));
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize Zoom settings", e);
                }
            }

            // Save conference
            conference = conferenceRepository.save(conference);

            // Link conference to event
            event.setConference(conference);
            eventRepository.save(event);

            log.info("Zoom meeting created for event: {}", eventId);
            return conference;
        } catch (Exception e) {
            log.error("Error creating Zoom meeting for event: {}", eventId, e);
            throw new ZoomException("Failed to create Zoom meeting: " + e.getMessage(), e);
        }
    }

    /**
     * Update a Zoom meeting
     */
    @Transactional
    public Conference updateZoomMeeting(String eventId, String userId) {
        try {
            // Get event
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

            // Get conference
            Conference conference = event.getConference();
            if (conference == null || conference.getZoomMeetingId() == null) {
                throw new ResourceNotFoundException("Zoom meeting not found for event");
            }

            // Get access token
            String accessToken = getValidAccessToken(userId);

            // Create meeting request
            ZoomMeetingRequest meetingRequest = buildMeetingRequest(event);

            // Call Zoom API
            zoomApiClient.updateMeeting(accessToken, conference.getZoomMeetingId(), meetingRequest);

            // Update conference entity
            conference.setDuration(meetingRequest.getDuration());
            conference.setAgenda(meetingRequest.getAgenda());
            conference.setTimezone(meetingRequest.getTimezone());
            conference.setLastSynced(Instant.now());

            // Store settings as JSON
            if (meetingRequest.getSettings() != null) {
                try {
                    conference.setSettings(objectMapper.writeValueAsString(meetingRequest.getSettings()));
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize Zoom settings", e);
                }
            }

            conference = conferenceRepository.save(conference);

            log.info("Zoom meeting updated for event: {}", eventId);
            return conference;
        } catch (Exception e) {
            log.error("Error updating Zoom meeting for event: {}", eventId, e);
            throw new RuntimeException("Failed to update Zoom meeting: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a Zoom meeting
     */
    @Transactional
    public void deleteZoomMeeting(String eventId, String userId) {
        try {
            // Get event
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

            // Get conference
            Conference conference = event.getConference();
            if (conference == null || conference.getZoomMeetingId() == null) {
                throw new ResourceNotFoundException("Zoom meeting not found for event");
            }

            // Get access token
            String accessToken = getValidAccessToken(userId);

            // Call Zoom API to delete meeting
            zoomApiClient.deleteMeeting(accessToken, conference.getZoomMeetingId());

            // Delete conference from database
            event.setConference(null);
            eventRepository.save(event);
            conferenceRepository.delete(conference);

            log.info("Zoom meeting deleted for event: {}", eventId);
        } catch (Exception e) {
            log.error("Error deleting Zoom meeting for event: {}", eventId, e);
            throw new RuntimeException("Failed to delete Zoom meeting: " + e.getMessage(), e);
        }
    }

    /**
     * Check if user is connected to Zoom
     */
    public boolean isConnected(String userId) {
        String accessToken = redisTemplate.opsForValue().get(ZOOM_TOKEN_KEY_PREFIX + userId);
        return accessToken != null;
    }

    public String getZakToken(String userId) {
        String accessToken = getValidAccessToken(userId);
        return zoomApiClient.getZakToken(accessToken);
    }

    public String getObfToken(String userId) {
        String accessToken = getValidAccessToken(userId);
        return zoomApiClient.getObfToken(accessToken);
    }

    /**
     * Get valid access token (refresh if needed)
     */
    private String getValidAccessToken(String userId) {
        String accessToken = redisTemplate.opsForValue().get(ZOOM_TOKEN_KEY_PREFIX + userId);

        if (accessToken == null) {
            // Try to refresh token
            String refreshToken = redisTemplate.opsForValue().get(ZOOM_REFRESH_TOKEN_KEY_PREFIX + userId);
            if (refreshToken != null) {
                try {
                    ZoomTokenResponse tokenResponse = zoomApiClient.refreshToken(refreshToken);
                    storeTokens(userId, tokenResponse);
                    return tokenResponse.getAccessToken();
                } catch (Exception e) {
                    log.error("Failed to refresh Zoom token for user: {}. The token might be invalid or expired.",
                            userId, e);
                    // If refresh fails with invalid_grant, we should probably clear the tokens
                    // so the user knows they need to reconnect.
                    if (e.getMessage().contains("invalid_grant") || e.getMessage().contains("400")
                            || e.getMessage().contains("401")) {
                        log.warn("Invalid Zoom grant detected for user: {}. Clearing stored tokens.", userId);
                        redisTemplate.delete(ZOOM_TOKEN_KEY_PREFIX + userId);
                        redisTemplate.delete(ZOOM_REFRESH_TOKEN_KEY_PREFIX + userId);
                    }
                    throw new ZoomException("Zoom connection expired. Please reconnect your Zoom account.", e);
                }
            }
            throw new ZoomException("Zoom not connected. Please connect your Zoom account.");
        }

        return accessToken;
    }

    /**
     * Store tokens in Redis
     */
    private void storeTokens(String userId, ZoomTokenResponse tokenResponse) {
        // Store access token with expiration
        redisTemplate.opsForValue().set(
                ZOOM_TOKEN_KEY_PREFIX + userId,
                tokenResponse.getAccessToken(),
                Duration.ofSeconds(tokenResponse.getExpiresIn() - 300) // Subtract 5 minutes for safety
        );

        // Store refresh token (no expiration, but should be rotated)
        if (tokenResponse.getRefreshToken() != null) {
            redisTemplate.opsForValue().set(
                    ZOOM_REFRESH_TOKEN_KEY_PREFIX + userId,
                    tokenResponse.getRefreshToken());
        }
    }

    /**
     * Build Zoom meeting request from event
     */
    private ZoomMeetingRequest buildMeetingRequest(Event event) {
        // Format start time for Zoom (ISO 8601)
        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        String startTime = event.getStartTime()
                .atZone(ZoneId.systemDefault())
                .format(formatter);

        // Calculate duration in minutes
        int duration = (int) Duration.between(event.getStartTime(), event.getEndTime()).toMinutes();

        // Meeting settings
        Map<String, Object> settings = new HashMap<>();
        settings.put("host_video", true);
        settings.put("participant_video", true);
        settings.put("join_before_host", false);
        settings.put("mute_upon_entry", true);
        settings.put("waiting_room", true);
        settings.put("auto_recording", "cloud");

        return ZoomMeetingRequest.builder()
                .topic(event.getTitle())
                .type(2) // Scheduled meeting
                .startTime(startTime)
                .duration(duration)
                .timezone(ZoneId.systemDefault().toString())
                .agenda(event.getDescription())
                .settings(settings)
                .build();
    }
}
