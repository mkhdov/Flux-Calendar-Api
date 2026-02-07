package com.flux.calendar_service.zoom;

import com.flux.calendar_service.conference.Conference;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/zoom")
@RequiredArgsConstructor
@Tag(name = "Zoom Integration", description = "Endpoints for Zoom meeting integration")
public class ZoomController {

        private final ZoomMeetingService zoomMeetingService;

        @org.springframework.beans.factory.annotation.Value("${zoom.client.id}")
        private String clientId;

        @org.springframework.beans.factory.annotation.Value("${zoom.client.redirect-uri}")
        private String redirectUri;

        @GetMapping("/connect/{userId}")
        @Operation(summary = "Connect Zoom account", description = "Redirects to Zoom OAuth authorization page")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "302", description = "Redirect to Zoom authorization page")
        })
        public RedirectView connectZoom(
                        @Parameter(description = "User ID from JWT token", hidden = true) @PathVariable String userId) {
                // Construct proper OAuth URL with state parameter
                String zoomAuthUrl = String.format(
                                "https://zoom.us/oauth/authorize?response_type=code&client_id=%s&redirect_uri=%s&state=%s",
                                clientId, redirectUri, userId);
                return new RedirectView(zoomAuthUrl);
        }

        @GetMapping("/callback")
        @Operation(summary = "OAuth callback", description = "Handles Zoom OAuth callback")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Successfully connected Zoom"),
                        @ApiResponse(responseCode = "400", description = "Invalid authorization code"),
                        @ApiResponse(responseCode = "500", description = "Internal server error")
        })
        public ResponseEntity<Map<String, Object>> handleCallback(
                        @Parameter(description = "Authorization code from Zoom") @RequestParam String code,
                        @Parameter(description = "State parameter containing user ID") @RequestParam(required = false) String state,
                        @Parameter(description = "User ID from JWT token", hidden = true) @RequestHeader(value = "X-User-Id", required = false) String userId) {

                // Use state parameter if userId not in header
                String effectiveUserId = userId != null ? userId : state;

                Map<String, Object> result = zoomMeetingService.connectZoom(effectiveUserId, code);
                return ResponseEntity.ok(result);
        }

        @DeleteMapping("/disconnect")
        @Operation(summary = "Disconnect Zoom account", description = "Removes Zoom credentials for the user")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Successfully disconnected Zoom"),
                        @ApiResponse(responseCode = "500", description = "Internal server error")
        })
        public ResponseEntity<Map<String, Object>> disconnectZoom(
                        @Parameter(description = "User ID from JWT token", hidden = true) @RequestHeader("X-User-Id") String userId) {

                zoomMeetingService.disconnectZoom(userId);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Zoom disconnected successfully");

                return ResponseEntity.ok(response);
        }

        @PostMapping("/events/{eventId}/meeting")
        @Operation(summary = "Create Zoom meeting", description = "Creates a Zoom meeting for the specified event")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Successfully created Zoom meeting"),
                        @ApiResponse(responseCode = "404", description = "Event not found"),
                        @ApiResponse(responseCode = "401", description = "Zoom not connected"),
                        @ApiResponse(responseCode = "500", description = "Internal server error")
        })
        public ResponseEntity<Map<String, Object>> createMeeting(
                        @Parameter(description = "Event ID") @PathVariable String eventId,
                        @Parameter(description = "User ID from JWT token", hidden = true) @RequestHeader("X-User-Id") String userId) {

                Conference conference = zoomMeetingService.createZoomMeeting(eventId, userId);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("meeting", conference);
                response.put("joinUrl", conference.getConferenceLink());
                response.put("message", "Zoom meeting created successfully");

                return ResponseEntity.ok(response);
        }

        @PatchMapping("/events/{eventId}/meeting")
        @Operation(summary = "Update Zoom meeting", description = "Updates an existing Zoom meeting for the specified event")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Successfully updated Zoom meeting"),
                        @ApiResponse(responseCode = "404", description = "Event or meeting not found"),
                        @ApiResponse(responseCode = "401", description = "Zoom not connected"),
                        @ApiResponse(responseCode = "500", description = "Internal server error")
        })
        public ResponseEntity<Map<String, Object>> updateMeeting(
                        @Parameter(description = "Event ID") @PathVariable String eventId,
                        @Parameter(description = "User ID from JWT token", hidden = true) @RequestHeader("X-User-Id") String userId) {

                Conference conference = zoomMeetingService.updateZoomMeeting(eventId, userId);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("meeting", conference);
                response.put("message", "Zoom meeting updated successfully");

                return ResponseEntity.ok(response);
        }

        @DeleteMapping("/events/{eventId}/meeting")
        @Operation(summary = "Delete Zoom meeting", description = "Deletes the Zoom meeting for the specified event")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Successfully deleted Zoom meeting"),
                        @ApiResponse(responseCode = "404", description = "Event or meeting not found"),
                        @ApiResponse(responseCode = "401", description = "Zoom not connected"),
                        @ApiResponse(responseCode = "500", description = "Internal server error")
        })
        public ResponseEntity<Map<String, Object>> deleteMeeting(
                        @Parameter(description = "Event ID") @PathVariable String eventId,
                        @Parameter(description = "User ID from JWT token", hidden = true) @RequestHeader("X-User-Id") String userId) {

                zoomMeetingService.deleteZoomMeeting(eventId, userId);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Zoom meeting deleted successfully");

                return ResponseEntity.ok(response);
        }

        @GetMapping("/status")
        @Operation(summary = "Check Zoom connection status", description = "Checks if the user has connected their Zoom account")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Successfully retrieved status")
        })
        public ResponseEntity<Map<String, Object>> checkStatus(
                        @Parameter(description = "User ID from JWT token", hidden = true) @RequestHeader("X-User-Id") String userId) {

                boolean isConnected = zoomMeetingService.isConnected(userId);

                Map<String, Object> response = new HashMap<>();
                response.put("connected", isConnected);

                return ResponseEntity.ok(response);
        }

        @GetMapping("/token/zak")
        @Operation(summary = "Get Zoom Access Key (ZAK) token", description = "Retrieves a ZAK token required to start meetings via the Meeting SDK")
        public ResponseEntity<Map<String, Object>> getZakToken(
                        @Parameter(description = "User ID from JWT token", hidden = true) @RequestHeader("X-User-Id") String userId) {
                String token = zoomMeetingService.getZakToken(userId);
                Map<String, Object> response = new HashMap<>();
                response.put("token", token);
                response.put("type", "zak");
                return ResponseEntity.ok(response);
        }

        @GetMapping("/token/obf")
        @Operation(summary = "Get On Behalf Of (OBF) token", description = "Retrieves an OBF token required to join external meetings via the Meeting SDK")
        public ResponseEntity<Map<String, Object>> getObfToken(
                        @Parameter(description = "User ID from JWT token", hidden = true) @RequestHeader("X-User-Id") String userId) {
                String token = zoomMeetingService.getObfToken(userId);
                Map<String, Object> response = new HashMap<>();
                response.put("token", token);
                response.put("type", "obf");
                return ResponseEntity.ok(response);
        }
}
