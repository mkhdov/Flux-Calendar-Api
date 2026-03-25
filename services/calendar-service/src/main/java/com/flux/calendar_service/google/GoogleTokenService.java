package com.flux.calendar_service.google;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import com.flux.calendar_service.connects.ConnectService;
import com.flux.calendar_service.exception.GoogleTokenRevokedException;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import java.time.Clock;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.services.calendar.CalendarScopes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@ConditionalOnProperty(name = "google.calendar.enabled", havingValue = "true", matchIfMissing = false)
public class GoogleTokenService {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKEN_SERVER_URL = "https://oauth2.googleapis.com/token";
    private static final long REFRESH_SKEW_SECONDS = 300L;

    private final GoogleOAuthTokenRepository tokenRepository;
    private final ConnectService connectService;
    private final HttpTransport httpTransport;
    private final Clock clock;

    private GoogleAuthorizationCodeFlow flow;

    @Value("${google.calendar.client-id:#{null}}")
    private String clientId;

    @Value("${google.calendar.client-secret:#{null}}")
    private String clientSecret;

    @org.springframework.beans.factory.annotation.Autowired
    public GoogleTokenService(
            GoogleOAuthTokenRepository tokenRepository,
            HttpTransport httpTransport, ConnectService connectService) {
        this(tokenRepository, connectService, httpTransport, Clock.systemUTC());
    }

    GoogleTokenService(
            GoogleOAuthTokenRepository tokenRepository, ConnectService connectService,
            HttpTransport httpTransport,
            Clock clock) {
        this.tokenRepository = tokenRepository;
        this.connectService = connectService;
        this.httpTransport = httpTransport;
        this.clock = clock;
    }

    @jakarta.annotation.PostConstruct
    public void initialize() throws IOException {
        if (clientId == null || clientSecret == null) {
            log.error("Google Client ID or Secret is missing in configuration!");
            return;
        }

        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details()
                .setClientId(clientId.trim())
                .setClientSecret(clientSecret.trim());
        GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setWeb(details);

        this.flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets,
                java.util.Collections.singletonList(CalendarScopes.CALENDAR))
                .setDataStoreFactory(new MemoryDataStoreFactory())
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .build();
    }

    public GoogleAuthorizationCodeFlow getFlow() {
        return flow;
    }

    public Optional<GoogleOAuthToken> findToken(String userId) {
        return tokenRepository.findByUserId(userId);
    }

    public void deleteToken(String userId) {
        tokenRepository.deleteById(userId);
//      calling method for disconnect to mark google as connected -->
        connectService.disconnectGoogle(userId);
    }

    public Credential saveTokenFromResponse(String userId, TokenResponse response) {
        GoogleOAuthToken token = tokenRepository.findByUserId(userId)
                .orElseGet(() -> GoogleOAuthToken.builder().userId(userId).build());

        token.setAccessToken(response.getAccessToken());
        if (response.getRefreshToken() != null && !response.getRefreshToken().isBlank()) {
            token.setRefreshToken(response.getRefreshToken());
        }
        token.setTokenType(response.getTokenType());
        token.setScope(response.getScope());
        token.setUpdatedAt(Instant.now(clock));

        if (response.getExpiresInSeconds() != null) {
            token.setExpiresAt(Instant.now(clock).plusSeconds(response.getExpiresInSeconds()));
        } else {
            token.setExpiresAt(null);
        }

        tokenRepository.save(token);
        return buildCredential(token);
    }

    public Credential buildCredential(GoogleOAuthToken token) {
        Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                .setTransport(httpTransport)
                .setJsonFactory(JSON_FACTORY)
                .setTokenServerEncodedUrl(TOKEN_SERVER_URL)
                .setClientAuthentication(new ClientParametersAuthentication(clientId, clientSecret))
                .build();

        credential.setAccessToken(token.getAccessToken());
        credential.setRefreshToken(token.getRefreshToken());
        credential.setExpiresInSeconds(calculateExpiresInSeconds(token.getExpiresAt()));
        return credential;
    }

    private Long calculateExpiresInSeconds(Instant expiresAt) {
        if (expiresAt == null) {
            return null;
        }
        long seconds = expiresAt.getEpochSecond() - Instant.now(clock).getEpochSecond();
        return Math.max(seconds, 0L);
    }

    @Transactional
    public Credential getValidCredential(String userId) throws IOException {
        GoogleOAuthToken token = tokenRepository.findByUserId(userId).orElse(null);
        if (token == null) {
            return null;
        }
        if (!token.isExpiringSoon(REFRESH_SKEW_SECONDS)) {
            return buildCredential(token);
        }

        GoogleOAuthToken locked = tokenRepository.findByUserIdForUpdate(userId).orElse(null);
        if (locked == null) {
            return null;
        }
        if (!locked.isExpiringSoon(REFRESH_SKEW_SECONDS)) {
            return buildCredential(locked);
        }

        return refreshLockedToken(locked);
    }

    @Transactional
    public Credential refreshAccessToken(String userId) throws IOException {
        GoogleOAuthToken locked = tokenRepository.findByUserIdForUpdate(userId).orElse(null);
        if (locked == null) {
            return null;
        }
        return refreshLockedToken(locked);
    }

    private Credential refreshLockedToken(GoogleOAuthToken locked) throws IOException {
        if (locked.getRefreshToken() == null || locked.getRefreshToken().isBlank()) {
            tokenRepository.deleteById(locked.getUserId());
            throw new GoogleTokenRevokedException(locked.getUserId());
        }

        Credential credential = buildCredential(locked);
        try {
            boolean refreshed = credential.refreshToken();
            if (refreshed) {
                locked.setAccessToken(credential.getAccessToken());
                locked.setUpdatedAt(Instant.now(clock));

                if (credential.getExpiresInSeconds() != null) {
                    locked.setExpiresAt(Instant.now(clock).plusSeconds(credential.getExpiresInSeconds()));
                } else {
                    locked.setExpiresAt(null);
                }

                if (credential.getRefreshToken() != null && !credential.getRefreshToken().isBlank()) {
                    locked.setRefreshToken(credential.getRefreshToken());
                }
                tokenRepository.save(locked);
            }
            return credential;
        } catch (TokenResponseException e) {
            if (GoogleAuthService.isInvalidGrant(e)) {
                tokenRepository.deleteById(locked.getUserId());
                throw new GoogleTokenRevokedException(locked.getUserId(), e);
            }
            throw e;
        }
    }
}
