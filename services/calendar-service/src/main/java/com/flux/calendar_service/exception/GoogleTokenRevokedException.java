package com.flux.calendar_service.exception;

/**
 * Thrown when a Google OAuth2 refresh token is permanently invalid
 * (invalid_grant).
 * This is a terminal error — the user must go through the OAuth consent flow
 * again.
 * Unlike transient IOExceptions, this must never be silently swallowed or
 * retried.
 */
public class GoogleTokenRevokedException extends RuntimeException {

    private final String userId;

    public GoogleTokenRevokedException(String userId) {
        super("Google OAuth token permanently revoked for user: " + userId
                + ". User must re-authorize via /auth/google.");
        this.userId = userId;
    }

    public GoogleTokenRevokedException(String userId, Throwable cause) {
        super("Google OAuth token permanently revoked for user: " + userId
                + ". User must re-authorize via /auth/google.", cause);
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}
