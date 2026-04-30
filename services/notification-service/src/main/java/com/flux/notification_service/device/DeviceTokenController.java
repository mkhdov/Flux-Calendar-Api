package com.flux.notification_service.device;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    /**
     * Flutter calls this on login and whenever Firebase refreshes the FCM token.
     * Body: { "userId": "...", "deviceId": "...", "fcmToken": "..." }
     */
    @PostMapping("/token")
    public ResponseEntity<Void> registerToken(@RequestBody DeviceTokenRequest request) {
        deviceTokenService.upsertToken(request.userId(), request.deviceId(), request.fcmToken());
        return ResponseEntity.ok().build();
    }

    /**
     * Flutter calls this on logout.
     * Body: { "userId": "...", "deviceId": "..." }
     */
    @DeleteMapping("/token")
    public ResponseEntity<Void> removeToken(@RequestBody DeviceTokenRemoveRequest request) {
        deviceTokenService.removeToken(request.userId(), request.deviceId());
        return ResponseEntity.noContent().build();
    }

    public record DeviceTokenRequest(String userId, String deviceId, String fcmToken) {}
    public record DeviceTokenRemoveRequest(String userId, String deviceId) {}
}
