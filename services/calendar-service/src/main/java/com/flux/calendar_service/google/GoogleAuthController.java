package com.flux.calendar_service.google;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.api.client.auth.oauth2.Credential;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.flux.calendar_service.exception.UnauthorizedException;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@ConditionalOnProperty(name = "google.calendar.enabled", havingValue = "true", matchIfMissing = false)
@Tag(name = "Google Auth", description = "Endpoints for Google Calendar OAuth2 authentication")
public class GoogleAuthController {

    @Autowired
    private GoogleAuthService authService;

    /**
     * Step 1: Initiate OAuth flow
     * GET http://localhost:8080/auth/google
     *
     * This redirects user to Google's consent page
     */
    @Operation(summary = "Initiate Google OAuth flow", description = "Redirects the user to Google's consent page to authorize calendar access")
    @GetMapping("/google")
    public RedirectView authenticateWithGoogle(
            @Parameter(description = "ID of the user initiating authentication") @RequestParam("userId") String userId,
            HttpSession session) {
        try {
            // Generate authorization URL
            String authUrl = authService.getAuthorizationUrl();

            // Store state and userId in session
            session.setAttribute("oauth_state", "random_state_" + System.currentTimeMillis());
            session.setAttribute("oauth_user_id", userId);

            System.out.println("🔐 Redirecting to Google OAuth for user: " + userId);
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
            @Parameter(description = "Authorization code from Google") @RequestParam("code") String authorizationCode,
            @Parameter(description = "State parameter for security") @RequestParam(value = "state", required = false) String state,
            HttpSession session) {

        try {
            System.out.println("📥 Received callback from Google");
            System.out.println("Authorization code: " + authorizationCode.substring(0, 20) + "...");

            // Retrieve userId from session
            String userId = (String) session.getAttribute("oauth_user_id");
            if (userId == null) {
                throw new UnauthorizedException("User ID not found in session");
            }

            // Exchange authorization code for access token
            Credential credential = authService.exchangeCodeForToken(authorizationCode, userId);

            // Store credential in session
            session.setAttribute("google_credential", credential);
            session.setAttribute("user_authenticated", true);
            session.setAttribute("user_id", userId);

            System.out.println("✅ Authentication successful for user: " + userId);
            System.out.println("Access Token: " + credential.getAccessToken().substring(0, 20) + "...");
            System.out.println(
                    "Refresh Token: " + (credential.getRefreshToken() != null ? "Available" : "Not available"));

            return Map.of(
                    "status", "success",
                    "message", "Successfully authenticated with Google",
                    "userId", userId,
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
     * Check authentication status
     * GET http://localhost:8080/auth/status
     */
    @Operation(summary = "Check authentication status", description = "Checks if the current session is authenticated with Google")
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
}
