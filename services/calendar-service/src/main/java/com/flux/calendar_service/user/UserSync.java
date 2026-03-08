package com.flux.calendar_service.user;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_sync")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserSync {

    @Id
    @EqualsAndHashCode.Include
    private String id;

    private String email;
    private String firstname;
    private String lastname;

    /**
     * TRUE while the user has a valid Google OAuth token stored.
     * Set to FALSE when an invalid_grant error is detected, preventing
     * the scheduler from retrying with a permanently revoked token.
     */
    @Column(name = "google_connected", nullable = false)
    @Builder.Default
    private boolean googleConnected = false;

    /**
     * Timestamp of the moment the token was detected as revoked.
     * Used to show "reconnect" prompts on the frontend.
     */
    @Column(name = "google_disconnected_at")
    private OffsetDateTime googleDisconnectedAt;

    /**
     * The Google account email that was connected (e.g. user@gmail.com).
     * Populated on successful OAuth and used to display connected account info.
     */
    @Column(name = "google_email")
    private String googleEmail;
}
