package com.flux.notification_service.reminder;

import java.time.Instant;

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
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
    name = "reminders",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_reminder",
            columnNames = {"event_id", "trigger_time", "type"}
        )
    },
    indexes = {
        @Index(name = "idx_reminders_status_trigger", columnList = "status, trigger_time"),
        @Index(name = "idx_reminders_event_id", columnList = "event_id"),
        @Index(name = "idx_reminders_user_id", columnList = "user_id")
    }
)
@EntityListeners(AuditingEntityListener.class)
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private String id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "trigger_time", nullable = false)
    private Instant triggerTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ReminderType type;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReminderStatus status = ReminderStatus.PENDING;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "sent_at")
    private Instant sentAt;

    // ─── Event snapshot fields (populated from Kafka) ───
    @Column(name = "event_title")
    private String eventTitle;

    @Column(name = "event_description", columnDefinition = "TEXT")
    private String eventDescription;

    @Column(name = "event_start_time")
    private Instant eventStartTime;

    @Column(name = "event_end_time")
    private Instant eventEndTime;

    @Column(name = "all_day")
    private Boolean allDay;

    @Column(name = "location_text")
    private String locationText;

    @Column(name = "meeting_type", length = 30)
    private String meetingType;

    @Column(name = "join_url", length = 1000)
    private String joinUrl;

    @Column(name = "meeting_code", length = 128)
    private String meetingCode;

    @Column(name = "dial_in_phone", length = 64)
    private String dialInPhone;

    @Column(name = "dial_in_pin", length = 64)
    private String dialInPin;

    @Column(name = "meeting_password", length = 128)
    private String meetingPassword;

    @Column(name = "subtasks_json", columnDefinition = "TEXT")
    private String subtasksJson;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
