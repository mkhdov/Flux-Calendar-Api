package com.flux.calendar_service.zoom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flux.calendar_service.zoom.dto.ZoomMeetingRequest;
import com.flux.calendar_service.zoom.dto.ZoomMeetingResponse;
import com.flux.calendar_service.zoom.dto.ZoomTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZoomApiClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    @Value("${zoom.client.id:}")
    private String clientId;

    @Value("${zoom.client.secret:}")
    private String clientSecret;

    @Value("${zoom.client.redirect-uri:}")
    private String redirectUri;

    @Value("${zoom.api.base-url:https://api.zoom.us/v2}")
    private String baseUrl;

    @Value("${zoom.api.token-url:https://zoom.us/oauth/token}")
    private String tokenUrl;

    /**
     * Get Access Token using OAuth authorization code
     */
    public ZoomTokenResponse getAccessToken(String authorizationCode) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String credentials = clientId + ":" + clientSecret;
            String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
            headers.add("Authorization", "Basic " + encodedCredentials);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("grant_type", "authorization_code");
            map.add("code", authorizationCode);
            map.add("redirect_uri", redirectUri);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                return ZoomTokenResponse.builder()
                        .accessToken((String) body.get("access_token"))
                        .tokenType((String) body.get("token_type"))
                        .refreshToken((String) body.get("refresh_token"))
                        .expiresIn(((Number) body.get("expires_in")).longValue())
                        .scope((String) body.get("scope"))
                        .build();
            }
            throw new RuntimeException("Failed to get access token");
        } catch (HttpClientErrorException e) {
            log.error("Error getting Zoom access token: {}", e.getResponseBodyAsString());
            throw new RuntimeException("Failed to get Zoom access token: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error getting Zoom access token", e);
            throw new RuntimeException("Failed to get Zoom access token: " + e.getMessage(), e);
        }
    }

    /**
     * Refresh Access Token using refresh token
     */
    public ZoomTokenResponse refreshToken(String refreshToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String credentials = clientId + ":" + clientSecret;
            String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
            headers.add("Authorization", "Basic " + encodedCredentials);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("grant_type", "refresh_token");
            map.add("refresh_token", refreshToken);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                return ZoomTokenResponse.builder()
                        .accessToken((String) body.get("access_token"))
                        .tokenType((String) body.get("token_type"))
                        .refreshToken((String) body.get("refresh_token"))
                        .expiresIn(((Number) body.get("expires_in")).longValue())
                        .scope((String) body.get("scope"))
                        .build();
            }
            throw new RuntimeException("Failed to refresh token");
        } catch (HttpClientErrorException e) {
            log.error("Error refreshing Zoom token: {}", e.getResponseBodyAsString());
            throw new RuntimeException("Failed to refresh Zoom token: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error refreshing Zoom token", e);
            throw new RuntimeException("Failed to refresh Zoom token: " + e.getMessage(), e);
        }
    }

    /**
     * Create a Zoom meeting
     */
    public ZoomMeetingResponse createMeeting(String accessToken, ZoomMeetingRequest meetingRequest) {
        try {
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<ZoomMeetingRequest> request = new HttpEntity<>(meetingRequest, headers);
            String url = baseUrl + "/users/me/meetings";

            log.info("Creating Zoom meeting: {}", meetingRequest.getTopic());
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
                return mapToZoomMeetingResponse(response.getBody());
            }
            throw new RuntimeException("Failed to create meeting: " + response.getStatusCode());
        } catch (HttpClientErrorException e) {
            log.error("Error creating Zoom meeting: {}", e.getResponseBodyAsString(), e);
            throw new RuntimeException("Failed to create Zoom meeting: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error creating Zoom meeting", e);
            throw new RuntimeException("Failed to create Zoom meeting: " + e.getMessage(), e);
        }
    }

    /**
     * Update a Zoom meeting
     */
    public void updateMeeting(String accessToken, String meetingId, ZoomMeetingRequest meetingRequest) {
        try {
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<ZoomMeetingRequest> request = new HttpEntity<>(meetingRequest, headers);
            String url = baseUrl + "/meetings/" + meetingId;

            log.info("Updating Zoom meeting: {}", meetingId);
            restTemplate.exchange(url, HttpMethod.PATCH, request, Void.class);
        } catch (HttpClientErrorException e) {
            log.error("Error updating Zoom meeting: {}", e.getResponseBodyAsString(), e);
            throw new RuntimeException("Failed to update Zoom meeting: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error updating Zoom meeting", e);
            throw new RuntimeException("Failed to update Zoom meeting: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a Zoom meeting
     */
    public void deleteMeeting(String accessToken, String meetingId) {
        try {
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            String url = baseUrl + "/meetings/" + meetingId;

            log.info("Deleting Zoom meeting: {}", meetingId);
            restTemplate.exchange(url, HttpMethod.DELETE, request, Void.class);
        } catch (HttpClientErrorException e) {
            log.error("Error deleting Zoom meeting: {}", e.getResponseBodyAsString(), e);
            throw new RuntimeException("Failed to delete Zoom meeting: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error deleting Zoom meeting", e);
            throw new RuntimeException("Failed to delete Zoom meeting: " + e.getMessage(), e);
        }
    }

    /**
     * Get user information
     */
    public Map<String, Object> getUserInfo(String accessToken) {
        try {
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            String url = baseUrl + "/users/me";

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            }
            throw new RuntimeException("Failed to get user info");
        } catch (Exception e) {
            log.error("Error getting Zoom user info", e);
            throw new RuntimeException("Failed to get Zoom user info: " + e.getMessage(), e);
        }
    }

    /**
     * Get Zoom Access Key (ZAK) token
     */
    public String getZakToken(String accessToken) {
        return getZoomTokenByType(accessToken, "zak");
    }

    /**
     * Get On Behalf Of (OBF) token
     */
    public String getObfToken(String accessToken) {
        return getZoomTokenByType(accessToken, "obf");
    }

    private String getZoomTokenByType(String accessToken, String type) {
        try {
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            String url = baseUrl + "/users/me/token?type=" + type;

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (String) response.getBody().get("token");
            }
            throw new RuntimeException("Failed to get " + type + " token");
        } catch (HttpClientErrorException e) {
            log.error("Error getting Zoom {} token: {}", type, e.getResponseBodyAsString());
            throw new RuntimeException("Failed to get Zoom " + type + " token: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error getting Zoom {} token", type, e);
            throw new RuntimeException("Failed to get Zoom " + type + " token: " + e.getMessage(), e);
        }
    }

    private HttpHeaders createHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + accessToken);
        return headers;
    }

    private ZoomMeetingResponse mapToZoomMeetingResponse(Map<String, Object> body) {
        return ZoomMeetingResponse.builder()
                .id(body.get("id") != null ? ((Number) body.get("id")).longValue() : null)
                .uuid((String) body.get("uuid"))
                .hostId((String) body.get("host_id"))
                .hostEmail((String) body.get("host_email"))
                .topic((String) body.get("topic"))
                .type(body.get("type") != null ? ((Number) body.get("type")).intValue() : null)
                .startTime((String) body.get("start_time"))
                .duration(body.get("duration") != null ? ((Number) body.get("duration")).intValue() : null)
                .timezone((String) body.get("timezone"))
                .agenda((String) body.get("agenda"))
                .startUrl((String) body.get("start_url"))
                .joinUrl((String) body.get("join_url"))
                .password((String) body.get("password"))
                .settings(body.get("settings") instanceof Map ? (Map<String, Object>) body.get("settings") : null)
                .build();
    }
}
