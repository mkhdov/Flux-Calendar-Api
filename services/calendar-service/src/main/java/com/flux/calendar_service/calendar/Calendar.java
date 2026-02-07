package com.flux.calendar_service.calendar;

import com.flux.calendar_service.event.Event;
import com.flux.calendar_service.task.Task;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "calendars")
@EntityListeners(AuditingEntityListener.class)
public class Calendar {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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

    @Column(name = "is_deleted", columnDefinition = "false")
    private boolean isDeleted;

    @Column(name = "created_at", updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Column(name = "last_google_sync")
    private LocalDateTime lastGoogleSync;

    @Column(name = "google_sync_token")
    private String googleSyncToken;

    @OneToMany(mappedBy = "calendar", cascade = CascadeType.ALL)
    private List<Event> events = new ArrayList<>();

    @OneToMany(mappedBy = "calendar", cascade = CascadeType.ALL)
    private List<Task> tasks = new ArrayList<>();
}
