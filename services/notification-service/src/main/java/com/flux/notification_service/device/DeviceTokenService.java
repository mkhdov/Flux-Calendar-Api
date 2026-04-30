package com.flux.notification_service.device;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    /**
     * Register or update the FCM token for a specific user + device combination.
     * Called by the Flutter app on every login and whenever Firebase rotates the token.
     */
    @Transactional
    public void upsertToken(String userId, String deviceId, String fcmToken) {
        deviceTokenRepository.findByUserIdAndDeviceId(userId, deviceId)
                .ifPresentOrElse(
                        existing -> {
                            existing.setFcmToken(fcmToken);
                            deviceTokenRepository.save(existing);
                            log.info("Updated FCM token for user {} device {}", userId, deviceId);
                        },
                        () -> {
                            DeviceToken token = DeviceToken.builder()
                                    .userId(userId)
                                    .deviceId(deviceId)
                                    .fcmToken(fcmToken)
                                    .build();
                            deviceTokenRepository.save(token);
                            log.info("Registered new FCM token for user {} device {}", userId, deviceId);
                        }
                );
    }

    /**
     * Returns all FCM tokens for a user (they may have multiple devices).
     */
    public List<String> getTokensForUser(String userId) {
        return deviceTokenRepository.findByUserId(userId)
                .stream()
                .map(DeviceToken::getFcmToken)
                .collect(Collectors.toList());
    }

    /**
     * Remove a device token (e.g. on logout).
     */
    @Transactional
    public void removeToken(String userId, String deviceId) {
        deviceTokenRepository.deleteByUserIdAndDeviceId(userId, deviceId);
        log.info("Removed FCM token for user {} device {}", userId, deviceId);
    }
}
