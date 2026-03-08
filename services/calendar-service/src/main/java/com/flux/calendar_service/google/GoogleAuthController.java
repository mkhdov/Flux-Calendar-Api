package com.flux.calendar_service.google;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.flux.calendar_service.calendar.Calendar;
import com.flux.calendar_service.calendar.CalendarRepository;
import com.flux.calendar_service.exception.UnauthorizedException;
import com.flux.calendar_service.user.UserSyncRepository;
import com.google.api.client.auth.oauth2.Credential;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/auth")
@ConditionalOnProperty(name = "google.calendar.enabled", havingValue = "true", matchIfMissing = false)
@Tag(name = "Google Auth", description = "Endpoints for Google Calendar OAuth2 authentication")
public class GoogleAuthController {

    private static final String GOOGLE_SYNC_CALENDAR_COLOR = "#4285F4";

    @Autowired
    private GoogleAuthService authService;

    @Autowired
    private CalendarRepository calendarRepository;

    @Autowired
    private UserSyncRepository userSyncRepository;

    @Autowired(required = false)
    private GoogleCalendarApiService googleCalendarApiService;

    /**
     * Step 1: Initiate OAuth flow
     * GET http://localhost:8083/auth/google
     *
     * This redirects user to Google's consent page
     */
    @Operation(summary = "Initiate Google OAuth flow", description = "Redirects the user to Google's consent page to authorize calendar access")
    @GetMapping("/google")
    public RedirectView authenticateWithGoogle(
            @Parameter(description = "ID of the user initiating authentication") @RequestParam("userId") String userId,
            HttpSession session) {
        try {
            String callbackRedirectUri = authService.getConfiguredRedirectUri();

            String state = UUID.randomUUID().toString();

            // Generate authorization URL
            String authUrl = UriComponentsBuilder
                    .fromUriString(authService.getAuthorizationUrl(callbackRedirectUri))
                    .queryParam("state", state)
                    .build(true)
                    .toUriString();

            // Store state and userId in session
            session.setAttribute("oauth_state", state);
            session.setAttribute("oauth_user_id", userId);
            session.setAttribute("oauth_redirect_uri", callbackRedirectUri);

            System.out.println("🔐 Redirecting to Google OAuth for user: " + userId);
            System.out.println("OAuth callback URI: " + callbackRedirectUri);
            System.out.println("Auth URL: " + authUrl);

            return new RedirectView(authUrl);

        } catch (Exception e) {
            System.err.println("❌ Error initiating OAuth: " + e.getMessage());
            return new RedirectView("/auth/error");
        }
    }

    /**
     * Step 2: OAuth callback endpoint
     * GET http://localhost:8080/auth/google/callback?code=xxxxx
     *
     * Google redirects here after user grants permission
     */
    @Operation(summary = "Google OAuth callback", description = "Endpoint where Google redirects after user authorization. Exchanges code for tokens.")
    @ApiResponse(responseCode = "200", description = "Authentication successful")
    @GetMapping("/google/callback")
    public Map<String, Object> handleGoogleCallback(
            @Parameter(description = "Authorization code from Google") @RequestParam(value = "code", required = false) String authorizationCode,
            @Parameter(description = "OAuth error returned by Google") @RequestParam(value = "error", required = false) String error,
            @Parameter(description = "State parameter for security") @RequestParam(value = "state", required = false) String state,
            HttpSession session) {

        try {
            System.out.println("📥 Received callback from Google");

            if (error != null && !error.isBlank()) {
                String message = "Google OAuth failed: " + error;
                System.err.println("❌ " + message);
                return Map.of("status", "error", "message", message);
            }

            if (authorizationCode == null || authorizationCode.isBlank()) {
                throw new UnauthorizedException("Missing authorization code from Google callback");
            }

            String expectedState = (String) session.getAttribute("oauth_state");
            if (expectedState != null && (state == null || !expectedState.equals(state))) {
                throw new UnauthorizedException("Invalid OAuth state");
            }

            System.out.println("Authorization code: " + authorizationCode.substring(0, 20) + "...");

            // Retrieve userId from session
            String userId = (String) session.getAttribute("oauth_user_id");
            if (userId == null) {
                throw new UnauthorizedException("User ID not found in session");
            }

            String callbackRedirectUri = (String) session.getAttribute("oauth_redirect_uri");

            // Exchange authorization code for access token
            Credential credential = authService.exchangeCodeForToken(authorizationCode, userId, callbackRedirectUri);

            // Store credential in session
            session.setAttribute("google_credential", credential);
            session.setAttribute("user_authenticated", true);
            session.setAttribute("user_id", userId);

            if (googleCalendarApiService == null) {
                throw new UnauthorizedException("Google Calendar integration is not available");
            }
            String googleCalendarId = googleCalendarApiService.getPrimaryCalendarId(userId);
            Calendar googleSyncCalendar = ensureGoogleSyncCalendar(userId, googleCalendarId);

            // ── Mark user as Google-connected in DB ─────────────────────────────────
            // This clears any previous invalid_grant disconnect state so the scheduler
            // resumes syncing for this user on its next tick.
            authService.markUserAsConnected(userId, googleCalendarId);

            System.out.println("✅ Authentication successful for user: " + userId);
            System.out.println("Access Token: " + credential.getAccessToken().substring(0, 20) + "...");
            System.out.println(
                    "Refresh Token: " + (credential.getRefreshToken() != null ? "Available" : "Not available"));
            System.out.println("Google Sync Calendar: " + googleSyncCalendar.getId() + " (" + googleCalendarId + ")");

            return Map.of(
                    "status", "success",
                    "message", "Successfully authenticated with Google",
                    "userId", userId,
                    "googleCalendarId", googleCalendarId,
                    "googleSyncCalendarId", googleSyncCalendar.getId(),
                    "redirectUri", callbackRedirectUri,
                    "hasRefreshToken", credential.getRefreshToken() != null,
                    "expiresIn", credential.getExpiresInSeconds());

        } catch (Exception e) {
            System.err.println("❌ Error in callback: " + e.getMessage());
            e.printStackTrace();
            return Map.of(
                    "status", "error",
                    "message", "Authentication failed: " + e.getMessage());
        }
    }

    /**
     * Check authentication status (session-based).
     * GET /auth/status
     */
    @Operation(summary = "Check session authentication status")
    @GetMapping("/status")
    public Map<String, Object> checkAuthStatus(HttpSession session) {
        Boolean isAuthenticated = (Boolean) session.getAttribute("user_authenticated");
        Credential credential = (Credential) session.getAttribute("google_credential");

        if (isAuthenticated != null && isAuthenticated && credential != null) {
            return Map.of(
                    "authenticated", true,
                    "hasAccessToken", credential.getAccessToken() != null,
                    "hasRefreshToken", credential.getRefreshToken() != null,
                    "expiresIn", credential.getExpiresInSeconds() != null ? credential.getExpiresInSeconds() : 0);
        }

        return Map.of("authenticated", false);
    }

    /**
     * DB-backed Google connection status — frontend should call THIS, not
     * /auth/status.
     *
     * <p>
     * Unlike /auth/status (which reads from the HTTP session and resets on pod
     * restart),
     * this endpoint reads the {@code google_connected} column from the database, so
     * it
     * accurately reflects whether the user's refresh token is valid.
     *
     * <p>
     * Frontend contract:
     * <ul>
     * <li>Call on app load (after login) to decide whether to show a reconnect
     * banner</li>
     * <li>Call after a successful /auth/google/callback redirect to confirm
     * connection</li>
     * <li>If {@code googleConnected == false}, show: "Reconnect Google Calendar"
     * button</li>
     * <li>If {@code disconnectedAt} is present, show: "Disconnected on [date]"</li>
     * </ul>
     *
     * <p>
     * Response example (connected):
     * 
     * <pre>
     * { "googleConnected": true,  "googleEmail": "user@gmail.com", "disconnectedAt": null }
     * </pre>
     * 
     * Response example (revoked token):
     * 
     * <pre>
     * { "googleConnected": false, "googleEmail": null, "disconnectedAt": "2026-03-01T00:00:00Z" }
     * </pre>
     *
     * GET /auth/google/connection-status?userId={userId}
     */
    @Operation(summary = "Get DB-backed Google connection status for a user")
    @GetMapping("/google/connection-status")
    public Map<String, Object> getGoogleConnectionStatus(
            @RequestParam("userId") String userId) {

        return userSyncRepository.findById(userId)
                .<Map<String, Object>>map(u -> Map.of(
                        "googleConnected", u.isGoogleConnected(),
                        "googleEmail", u.getGoogleEmail() != null ? u.getGoogleEmail() : "",
                        "disconnectedAt", u.getGoogleDisconnectedAt() != null
                                ? u.getGoogleDisconnectedAt().toString()
                                : ""))
                .orElse(Map.of(
                        "googleConnected", false,
                        "googleEmail", "",
                        "disconnectedAt", ""));
    }

    /**
     * Logout and revoke access
     * POST http://localhost:8080/auth/logout
     */
    @Operation(summary = "Logout", description = "Invalidates the session and optionally revokes Google tokens")
    @PostMapping("/logout")
    public Map<String, String> logout(HttpSession session) {
        try {
            Credential credential = (Credential) session.getAttribute("google_credential");

            if (credential != null) {
                // Optionally revoke the token
                authService.revokeToken(credential);
            }

            // Clear session
            session.invalidate();

            System.out.println("✅ User logged out successfully");
            return Map.of("status", "success", "message", "Logged out successfully");

        } catch (Exception e) {
            System.err.println("❌ Error during logout: " + e.getMessage());
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    /**
     * Refresh access token
     * POST http://localhost:8080/auth/refresh
     */
    @Operation(summary = "Refresh token", description = "Refreshes the Google access token using the refresh token")
    @PostMapping("/refresh")
    public Map<String, Object> refreshToken(HttpSession session) {
        try {
            Credential credential = (Credential) session.getAttribute("google_credential");

            if (credential == null) {
                return Map.of("status", "error", "message", "Not authenticated");
            }

            // Refresh the token
            boolean refreshed = authService.refreshAccessToken(credential);

            if (refreshed) {
                // Update session with new credential
                session.setAttribute("google_credential", credential);

                return Map.of(
                        "status", "success",
                        "message", "Token refreshed successfully",
                        "expiresIn", credential.getExpiresInSeconds());
            } else {
                return Map.of("status", "error", "message", "Failed to refresh token");
            }

        } catch (Exception e) {
            System.err.println("❌ Error refreshing token: " + e.getMessage());
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    private Calendar ensureGoogleSyncCalendar(String userId, String googleCalendarId) {
        String legacyDescription = "Google sync calendar for " + googleCalendarId;
        Calendar activeCalendar = calendarRepository
                .findByUserIdAndGoogleCalendarIdAndIsDeletedFalse(userId, googleCalendarId)
                .or(() -> calendarRepository.findByUserIdAndTitleAndIsDeletedFalse(userId, googleCalendarId))
                .or(() -> calendarRepository.findByUserIdAndDescriptionAndIsDeletedFalse(userId, legacyDescription))
                .orElse(null);

        if (activeCalendar != null) {
            if (activeCalendar.getGoogleCalendarId() == null || activeCalendar.getGoogleCalendarId().isBlank()) {
                activeCalendar.setGoogleCalendarId(googleCalendarId);
                return calendarRepository.save(activeCalendar);
            }
            return activeCalendar;
        }

        Calendar recovered = calendarRepository
                .findByUserIdAndGoogleCalendarIdAndIsDeletedTrue(userId, googleCalendarId)
                .or(() -> calendarRepository.findByUserIdAndTitleAndIsDeletedTrue(userId, googleCalendarId))
                .or(() -> calendarRepository.findByUserIdAndDescriptionAndIsDeletedTrue(userId, legacyDescription))
                .orElse(null);
        if (recovered != null) {
            recovered.setDeleted(false);
            recovered.setPrimary(false);
            recovered.setGoogleCalendarId(googleCalendarId);
            return calendarRepository.save(recovered);
        }

        List<Calendar> userCalendars = calendarRepository.findCalendarByUserIdAndIsDeletedFalse(userId);
        String timezone = userCalendars.stream()
                .filter(Calendar::isPrimary)
                .map(Calendar::getTimezone)
                .filter(tz -> tz != null && !tz.isBlank())
                .findFirst()
                .orElse(ZoneId.systemDefault().getId());

        Calendar calendar = Calendar.builder()
                .userId(userId)
                .title(googleCalendarId)
                .description("Google sync calendar for " + googleCalendarId)
                .colorHex(GOOGLE_SYNC_CALENDAR_COLOR)
                .timezone(timezone)
                .isPrimary(false)
                .isDeleted(false)
                .googleCalendarId(googleCalendarId)
                .build();
        return calendarRepository.save(calendar);
    }
}
