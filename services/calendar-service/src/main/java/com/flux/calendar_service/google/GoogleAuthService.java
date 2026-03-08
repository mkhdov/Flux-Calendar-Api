package com.flux.calendar_service.google;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import com.flux.calendar_service.exception.GoogleTokenRevokedException;
import com.flux.calendar_service.user.UserSyncRepository;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.CalendarScopes;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@ConditionalOnProperty(name = "google.calendar.enabled", havingValue = "true", matchIfMissing = false)
public class GoogleAuthService {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);

    @Value("${google.calendar.client-id:#{null}}")
    private String clientId;

    @Value("${google.calendar.client-secret:#{null}}")
    private String clientSecret;

    @Value("${google.calendar.redirect-uri:http://localhost:8080/auth/google/callback}")
    private String redirectUri;

    private NetHttpTransport httpTransport;
    private GoogleAuthorizationCodeFlow flow;

    @Autowired
    private UserSyncRepository userSyncRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // INITIALISATION
    // ─────────────────────────────────────────────────────────────────────────

    @PostConstruct
    public void initialize() throws Exception {
        this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        initializeFlowFromConfig();
        log.info("Google OAuth Service initialized. Redirect URI: {}", redirectUri);
    }

    private void initializeFlowFromConfig() throws IOException {
        if (clientId == null || clientSecret == null) {
            log.error("Google Client ID or Secret is missing in configuration!");
            return;
        }

        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
        this.redirectUri = redirectUri.trim();

        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details()
                .setClientId(this.clientId)
                .setClientSecret(this.clientSecret);

        GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setWeb(details);

        FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(new java.io.File("tokens"));

        this.flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(dataStoreFactory)
                .setAccessType("offline")
                .setApprovalPrompt("force") // ensures a refresh token is always issued
                .build();

        log.info("Google OAuth flow initialized. Client ID: {}...",
                this.clientId.substring(0, Math.min(10, this.clientId.length())));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATIC UTILITY — INVALID_GRANT DETECTION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when the exception is a terminal {@code invalid_grant}
     * error.
     *
     * <p>
     * This is the <em>only</em> reliable way to distinguish a permanently revoked
     * refresh token from a transient network failure. Always call this before
     * deciding whether to retry or to mark the user as disconnected.
     *
     * <p>
     * Causes of {@code invalid_grant}:
     * <ul>
     * <li>User revoked access inside Google Account settings</li>
     * <li>Token unused for 6+ months (Google inactivity policy)</li>
     * <li>App in "Testing" mode and token is older than 7 days</li>
     * <li>50-token-per-user limit exceeded (oldest token invalidated)</li>
     * <li>User changed Google password after authorization</li>
     * </ul>
     */
    public static boolean isInvalidGrant(IOException e) {
        if (e instanceof TokenResponseException tre) {
            String error = tre.getDetails() != null ? tre.getDetails().getError() : null;
            return "invalid_grant".equals(error);
        }
        if (e instanceof com.google.api.client.googleapis.json.GoogleJsonResponseException gjre) {
            return gjre.getStatusCode() == 401;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AUTHORIZATION URL
    // ─────────────────────────────────────────────────────────────────────────

    public String getAuthorizationUrl() {
        if (flow == null) {
            throw new IllegalStateException(
                    "Google OAuth flow not initialized. Check your configuration.");
        }
        return flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .build();
    }

    public String getAuthorizationUrl(String callbackRedirectUri) {
        if (flow == null) {
            throw new IllegalStateException(
                    "Google OAuth flow not initialized. Check your configuration.");
        }
        String effectiveRedirectUri = (callbackRedirectUri != null && !callbackRedirectUri.isBlank())
                ? callbackRedirectUri.trim()
                : redirectUri;

        return flow.newAuthorizationUrl()
                .setRedirectUri(effectiveRedirectUri)
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOKEN EXCHANGE
    // ─────────────────────────────────────────────────────────────────────────

    public Credential exchangeCodeForToken(String authorizationCode, String userId) throws IOException {
        try {
            TokenResponse response = flow.newTokenRequest(authorizationCode)
                    .setRedirectUri(redirectUri)
                    .execute();
            Credential credential = flow.createAndStoreCredential(response, userId);
            log.info("Token exchange successful for user: {}", userId);
            return credential;
        } catch (IOException e) {
            log.error("Token exchange failed for user: {}. Code prefix: {}...",
                    userId, authorizationCode.substring(0, Math.min(10, authorizationCode.length())));
            throw e;
        }
    }

    public Credential exchangeCodeForToken(String authorizationCode, String userId,
            String callbackRedirectUri) throws IOException {
        String effectiveRedirectUri = (callbackRedirectUri != null && !callbackRedirectUri.isBlank())
                ? callbackRedirectUri.trim()
                : redirectUri;
        try {
            TokenResponse response = flow.newTokenRequest(authorizationCode)
                    .setRedirectUri(effectiveRedirectUri)
                    .execute();
            Credential credential = flow.createAndStoreCredential(response, userId);
            log.info("Token exchange successful for user: {}", userId);
            return credential;
        } catch (IOException e) {
            log.error("Token exchange failed for user: {}. Redirect URI: {}", userId, effectiveRedirectUri);
            throw e;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOKEN REFRESH
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Attempts to refresh the access token for the given user.
     *
     * <p>
     * <strong>Error handling contract:</strong>
     * <ul>
     * <li>{@link GoogleTokenRevokedException} — thrown when the refresh token is
     * permanently invalid ({@code invalid_grant}). The user is automatically
     * marked as disconnected in the DB. <strong>Do not retry.</strong></li>
     * <li>{@link IOException} — thrown for transient network or server errors.
     * Safe to log and retry on the next scheduler tick.</li>
     * </ul>
     *
     * @param credential the credential containing the refresh token
     * @param userId     the internal user ID (used for DB state updates and
     *                   logging)
     * @return {@code true} if the access token was refreshed successfully
     * @throws GoogleTokenRevokedException if the refresh token is permanently
     *                                     revoked
     * @throws IOException                 for transient failures
     */
    public boolean refreshAccessToken(Credential credential, String userId) throws IOException {
        if (credential.getRefreshToken() == null) {
            log.error("No refresh token stored for user: {}. Re-authorization required.", userId);
            markUserAsDisconnected(userId);
            throw new GoogleTokenRevokedException(userId);
        }

        try {
            boolean refreshed = credential.refreshToken();
            if (refreshed) {
                log.debug("Access token refreshed successfully for user: {}", userId);
            } else {
                log.warn("Token refresh returned false for user: {} — no new token issued.", userId);
            }
            return refreshed;

        } catch (TokenResponseException e) {
            if (isInvalidGrant(e)) {
                // ── TERMINAL ────────────────────────────────────────────────────────────
                // This refresh token can never be used again.
                // 1. Mark user disconnected in DB (scheduler will skip them next cycle)
                // 2. Purge the dead credential from the token store (no point keeping it)
                // 3. Throw a typed exception so callers can handle it intentionally
                String errorDescription = e.getDetails() != null
                        ? e.getDetails().getErrorDescription()
                        : "unknown";
                log.error("Google refresh token permanently revoked for user: {}. "
                        + "Reason: '{}'. User must re-authorize.", userId, errorDescription);
                markUserAsDisconnected(userId);
                purgeStoredCredential(userId);
                throw new GoogleTokenRevokedException(userId, e);
            }
            // Transient failure (network, server 5xx, clock skew, etc.) — safe to retry
            log.warn("Transient token refresh failure for user: {}. Will retry next cycle. Error: {}",
                    userId, e.getMessage());
            throw e;
        }
    }

    /**
     * Backward-compatible overload that delegates to
     * {@link #refreshAccessToken(Credential, String)} with userId="unknown".
     * Prefer the two-argument form whenever a userId is available.
     */
    public boolean refreshAccessToken(Credential credential) throws IOException {
        return refreshAccessToken(credential, "unknown");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DB STATE — CONNECTED / DISCONNECTED
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Marks the user as Google-disconnected in the DB.
     * The scheduler checks this flag before each sync attempt and will skip
     * this user until they re-authorize and {@link #markUserAsConnected} is called.
     */
    public void markUserAsDisconnected(String userId) {
        userSyncRepository.findById(userId).ifPresent(userSync -> {
            userSync.setGoogleConnected(false);
            userSync.setGoogleDisconnectedAt(OffsetDateTime.now());
            userSyncRepository.save(userSync);
            log.warn("User {} marked as Google-disconnected. "
                    + "Sync paused until user re-authorizes.", userId);
        });
    }

    public boolean isGoogleConnected(String userId) {
        return userSyncRepository.findById(userId)
                .map(com.flux.calendar_service.user.UserSync::isGoogleConnected)
                .orElse(false);
    }

    /**
     * Marks the user as Google-connected after a successful OAuth callback.
     * Clears the disconnect timestamp and stores the Google account email.
     *
     * @param userId      the internal user ID
     * @param googleEmail the Google email address from the calendar (e.g.
     *                    user@gmail.com)
     */
    public void markUserAsConnected(String userId, String googleEmail) {
        userSyncRepository.findById(userId).ifPresentOrElse(userSync -> {
            userSync.setGoogleConnected(true);
            userSync.setGoogleDisconnectedAt(null);
            userSync.setGoogleEmail(googleEmail);
            userSyncRepository.save(userSync);
            log.info("User {} marked as Google-connected (account: {}).", userId, googleEmail);
        }, () -> log.warn("markUserAsConnected: no UserSync record found for userId: {}. "
                + "The record may not have been created yet.", userId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOKEN REVOCATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Revokes the access token at Google's servers (logout).
     */
    public void revokeToken(Credential credential) throws IOException {
        if (credential.getAccessToken() != null) {
            httpTransport.createRequestFactory()
                    .buildGetRequest(new com.google.api.client.http.GenericUrl(
                            "https://oauth2.googleapis.com/revoke?token="
                                    + credential.getAccessToken()))
                    .execute();
            log.info("Google OAuth token revoked successfully.");
        }
    }

    /**
     * Removes a stored (broken) credential from the token DataStore so the
     * scheduler does not keep loading and reusing it.
     */
    private void purgeStoredCredential(String userId) {
        try {
            if (flow != null && flow.getCredentialDataStore() != null) {
                flow.getCredentialDataStore().delete(userId);
                log.info("Purged stored credential from DataStore for user: {}", userId);
            }
        } catch (IOException ex) {
            log.error("Failed to purge credential from DataStore for user: {}", userId, ex);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CREDENTIAL HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the credential has a valid access token with more
     * than 5 minutes remaining before expiry.
     */
    public boolean isCredentialValid(Credential credential) {
        if (credential == null || credential.getAccessToken() == null) {
            return false;
        }
        Long expiresIn = credential.getExpiresInSeconds();
        return expiresIn != null && expiresIn > 300;
    }

    /**
     * Loads the stored credential for a user from the token DataStore.
     * Returns {@code null} if no credential has been stored (user never authorized
     * or the credential was purged after an invalid_grant).
     */
    public Credential getLoadedCredential(String userId) throws IOException {
        if (flow == null || userId == null) {
            return null;
        }
        return flow.loadCredential(userId);
    }

    public GoogleAuthorizationCodeFlow getFlow() {
        return flow;
    }

    public String getConfiguredRedirectUri() {
        return redirectUri;
    }
}
