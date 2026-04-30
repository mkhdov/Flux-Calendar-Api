package com.flux.notification_service.device;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, String> {

    List<DeviceToken> findByUserId(String userId);

    Optional<DeviceToken> findByUserIdAndDeviceId(String userId, String deviceId);

    void deleteByUserIdAndDeviceId(String userId, String deviceId);
}
