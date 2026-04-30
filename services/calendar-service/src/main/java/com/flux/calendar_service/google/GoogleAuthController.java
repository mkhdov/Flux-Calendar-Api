package com.flux.calendar_service.google;

import java.time.ZoneId;
import java.util.Base64;
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

@RestController
@RequestMapping("/auth")
@ConditionalOnProperty(name = "google.calendar.enabled", havingValue = "true", matchIfMissing = false)
@Tag(name = "Google Auth", description = "Endpoints for Google Calendar OAuth2 authentication")
public class GoogleAuthController {

    private static final String GOOGLE_SYNC_CALENDAR_COLOR = "#4285F4";

    @Autowired
    private GoogleAuthService authService;

    @Autowired
    private GoogleTokenService googleTokenService;

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
     * Encodes userId + nonce into the OAuth state parameter (stateless — no session needed).
     */
    @Operation(summary = "Initiate Google OAuth flow", description = "Redirects the user to Google's consent page to authorize calendar access")
    @GetMapping("/google")
    public RedirectView authenticateWithGoogle(
            @Parameter(description = "ID of the user initiating authentication") @RequestParam("userId") String userId) {
        try {
            String callbackRedirectUri = authService.getConfiguredRedirectUri();

            // Encode userId + nonce into state — travels with the redirect, no session needed
            String nonce = UUID.randomUUID().toString();
            String state = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (userId + ":" + nonce).getBytes()
            );

            // Generate authorization URL
            String authUrl = UriComponentsBuilder
                    .fromUriString(authService.getAuthorizationUrl(callbackRedirectUri))
                    .queryParam("state", state)
                    .build(true)
                    .toUriString();

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
     * Decodes userId directly from the state parameter — works across any pod.
     */
    @Operation(summary = "Google OAuth callback", description = "Endpoint where Google redirects after user authorization. Exchanges code for tokens.")
    @ApiResponse(responseCode = "200", description = "Authentication successful")
    @GetMapping("/google/callback")
    public Map<String, Object> handleGoogleCallback(
            @Parameter(description = "Authorization code from Google") @RequestParam(value = "code", required = false) String authorizationCode,
            @Parameter(description = "OAuth error returned by Google") @RequestParam(value = "error", required = false) String error,
            @Parameter(description = "State parameter for security") @RequestParam(value = "state", required = false) String state) {

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

            // Decode state → "userId:nonce"
            if (state == null || state.isBlank()) {
                throw new UnauthorizedException("Missing OAuth state parameter");
            }

            String decoded;
            try {
                decoded = new String(Base64.getUrlDecoder().decode(state));
            } catch (IllegalArgumentException e) {
                throw new UnauthorizedException("Invalid OAuth state encoding");
            }

            String[] parts = decoded.split(":", 2);
            if (parts.length != 2) {
                throw new UnauthorizedException("Invalid OAuth state format");
            }

            // userId is now extracted directly from state — no session required
            String userId = parts[0];
            // parts[1] is the nonce — can be used for additional CSRF validation if needed

            if (userId == null || userId.isBlank()) {
                throw new UnauthorizedException("User ID not found in OAuth state");
            }

            String callbackRedirectUri = authService.getConfiguredRedirectUri();

            System.out.println("Authorization code: " + authorizationCode.substring(0, 20) + "...");

            // Exchange authorization code for access token
            Credential credential = authService.exchangeCodeForToken(authorizationCode, userId, callbackRedirectUri);

            if (googleCalendarApiService == null) {
                throw new UnauthorizedException("Google Calendar integration is not available");
            }
            String googleCalendarId = googleCalendarApiService.getPrimaryCalendarId(userId);
            Calendar googleSyncCalendar = ensureGoogleSyncCalendar(userId, googleCalendarId);

            // Mark user as Google-connected in DB
            authService.markUserAsConnected(userId, googleCalendarId);

            System.out.println("✅ Authentication successful for user: " + userId);
            System.out.println("Access Token: " + credential.getAccessToken().substring(0, 20) + "...");
            System.out.println("Refresh Token: " + (credential.getRefreshToken() != null ? "Available" : "Not available"));
            System.out.println("Google Sync Calendar: " + googleSyncCalendar.getId() + " (" + googleCalendarId + ")");

            return Map.of(
                    "status", "success",
                    "message", "Successfully authenticated with Google");

        } catch (Exception e) {
            System.err.println("❌ Error in callback: " + e.getMessage());
            e.printStackTrace();
            return Map.of(
                    "status", "error",
                    "message", "Authentication failed: " + e.getMessage());
        }
    }

    /**
     * Mobile Google OAuth linking
     * POST /auth/google/mobile-link
     */
    @Operation(summary = "Google OAuth mobile link", description = "Endpoint where mobile apps send serverAuthCode")
    @PostMapping("/google/mobile-link")
    public Map<String, Object> handleGoogleMobileLink(
            @RequestParam("userId") String userId,
            @org.springframework.web.bind.annotation.RequestBody Map<String, String> payload) {
        try {
            String serverAuthCode = payload.get("serverAuthCode");
            String email = payload.get("email");

            if (serverAuthCode == null || serverAuthCode.isBlank()) {
                throw new UnauthorizedException("Missing serverAuthCode from mobile client");
            }

            // For mobile flow, redirect URI is generally empty
            Credential credential = authService.exchangeCodeForToken(serverAuthCode, userId, "");

            String googleCalendarId = email;
            if (googleCalendarApiService != null) {
                try {
                    googleCalendarId = googleCalendarApiService.getPrimaryCalendarId(userId);
                } catch (Exception ignored) {}
            }
            if (googleCalendarId == null) {
                googleCalendarId = "primary";
            }

            ensureGoogleSyncCalendar(userId, googleCalendarId);
            authService.markUserAsConnected(userId, googleCalendarId);

            return Map.of("status", "success", "message", "Linked via mobile");
        } catch (Exception e) {
            System.err.println("❌ Error in mobile-link: " + e.getMessage());
            return Map.of("status", "error", "message", "Auth failed: " + e.getMessage());
        }
    }

    /**
     * Check authentication status (DB-based).
     * GET /auth/status
     */
    @Operation(summary = "Check DB authentication status")
    @GetMapping("/status")
    public Map<String, Object> checkAuthStatus(@RequestParam("userId") String userId) {
        return googleTokenService.findToken(userId)
                .<Map<String, Object>>map(token -> Map.of(
                        "authenticated", authService.isGoogleConnected(userId),
                        "hasAccessToken", token.getAccessToken() != null,
                        "hasRefreshToken", token.getRefreshToken() != null,
                        "expiresIn", token.getExpiresAt() != null
                                ? Math.max(0, token.getExpiresAt().getEpochSecond()
                                - java.time.Instant.now().getEpochSecond())
                                : 0))
                .orElse(Map.of("authenticated", false));
    }

    /**
     * DB-backed Google connection status — frontend should call THIS, not /auth/status.
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
    public Map<String, String> logout(@RequestParam("userId") String userId) {
        try {
            googleTokenService.findToken(userId).ifPresent(token -> {
                try {
                    authService.revokeToken(googleTokenService.buildCredential(token));
                } catch (Exception e) {
                    System.err.println("❌ Error during token revoke: " + e.getMessage());
                }
            });
            googleTokenService.deleteToken(userId);

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
    public Map<String, Object> refreshToken(@RequestParam("userId") String userId) {
        try {
            Credential credential = authService.refreshAccessToken(userId);
            if (credential == null) {
                return Map.of("status", "error", "message", "Not authenticated");
            }

            return Map.of(
                    "status", "success",
                    "message", "Token refreshed successfully",
                    "expiresIn", credential.getExpiresInSeconds());

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