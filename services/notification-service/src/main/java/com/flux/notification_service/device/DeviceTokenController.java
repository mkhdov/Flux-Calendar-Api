package com.flux.notification_service.device;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
@Slf4j
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    /**
     * Flutter calls this on login and whenever Firebase refreshes the FCM token.
     * Body: { "userId": "...", "deviceId": "...", "fcmToken": "..." }
     */
    @PostMapping("/token")
    public ResponseEntity<Void> registerToken(@RequestBody DeviceTokenRequest request) {
        log.info("Received request to register token for user: {}, device: {}", request.userId(), request.deviceId());
        deviceTokenService.upsertToken(request.userId(), request.deviceId(), request.fcmToken());
        return ResponseEntity.ok().build();
    }

    /**
     * Flutter calls this on logout.
     * Body: { "userId": "...", "deviceId": "..." }
     */
    @DeleteMapping("/token")
    public ResponseEntity<Void> removeToken(@RequestBody DeviceTokenRemoveRequest request) {
        log.info("Received request to remove token for user: {}, device: {}", request.userId(), request.deviceId());
        deviceTokenService.removeToken(request.userId(), request.deviceId());
        return ResponseEntity.noContent().build();
    }
}
