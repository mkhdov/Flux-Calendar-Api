package com.flux.calendar_service.calendar;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.flux.calendar_service.event.Event;
import com.flux.calendar_service.task.Task;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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
@Table(name = "calendars")
@EntityListeners(AuditingEntityListener.class)
public class Calendar {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private String id;

    @Column(name = "user_id", updatable = false, nullable = false)
    private String userId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "color_hex", nullable = false)
    private String colorHex;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "is_primary")
    private boolean isPrimary;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "created_at", updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(name = "updated_at")
    @LastModifiedDate
    private Instant updatedAt;

    @Column(name = "last_google_sync")
    private Instant lastGoogleSync;

    @Column(name = "google_sync_token")
    private String googleSyncToken;

    @Column(name = "google_calendar_id")
    private String googleCalendarId;

    @Builder.Default
    @OneToMany(mappedBy = "calendar", cascade = CascadeType.ALL)
    private List<Event> events = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "calendar", cascade = CascadeType.ALL)
    private List<Task> tasks = new ArrayList<>();
}
