package com.flux.notification_service.device;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
    name = "device_tokens",
    uniqueConstraints = @UniqueConstraint(name = "uq_user_device", columnNames = {"user_id", "device_id"}),
    indexes = @Index(name = "idx_device_tokens_user_id", columnList = "user_id")
)
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    /** Stable device identifier (from Flutter: Platform.isAndroid ? androidId : iosId) */
    @Column(name = "device_id", nullable = false, length = 256)
    private String deviceId;

    /** FCM registration token — refreshed whenever Firebase rotates it */
    @Column(name = "fcm_token", nullable = false, length = 4096)
    private String fcmToken;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
