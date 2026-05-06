package com.flux.notification_service.device;

public record DeviceTokenRequest(String userId, String deviceId, String fcmToken) {}
