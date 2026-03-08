package com.flux.calendar_service.conference;

import java.time.LocalDateTime;

import com.flux.calendar_service.event.Event;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "conferences")
@EntityListeners(AuditingEntityListener.class)
public class Conference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ConferenceType type;

    // Google Meet specific fields
    @Column(name = "google_conference_id")
    private String googleConferenceId; // e.g., "abc123xyz"

    @Column(name = "meet_link", length = 1000)
    private String meetLink; // https://meet.google.com/abc-defg-hij

    @Column(name = "meeting_code")
    private String meetingCode; // abc-defg-hij

    @Column(name = "phone_number")
    private String phoneNumber; // Dial-in number

    @Column(name = "pin")
    private String pin; // PIN for phone dial-in

    // For other platforms (Teams, Webex, etc.)
    @Column(name = "conference_link", length = 1000)
    private String conferenceLink;

    @Column(name = "conference_password")
    private String conferencePassword;

    @Column(name = "platform_name")
    private String platformName;

    // Zoom specific fields
    @Column(name = "zoom_meeting_id")
    private String zoomMeetingId; // Zoom's meeting ID

    @Column(name = "start_url", length = 1000)
    private String startUrl; // Host start URL

    @Column(name = "host_email")
    private String hostEmail; // Zoom host email

    @Column(name = "duration")
    private Integer duration; // Meeting duration in minutes

    @Column(name = "timezone")
    private String timezone; // Meeting timezone

    @Column(name = "agenda", columnDefinition = "TEXT")
    private String agenda; // Meeting agenda/description

    @Column(name = "settings", columnDefinition = "TEXT")
    private String settings; // JSON string of meeting settings

    // Sync tracking
    @Column(name = "sync_status")
    @Enumerated(EnumType.STRING)
    private SyncStatus syncStatus;

    @Column(name = "last_synced")
    private LocalDateTime lastSynced;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Relationship with Event (One-to-One)
    @OneToOne(mappedBy = "conference")
    private Event event;

    public enum ConferenceType {
        GOOGLE_MEET,
        ZOOM
    }

    public enum SyncStatus {
        SYNCED,
        PENDING_UPLOAD,
        PENDING_UPDATE,
        PENDING_DELETE
    }
}
