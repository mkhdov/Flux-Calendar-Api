package com.flux.notification_service.notification;

/**
 * Strategy interface for sending notifications through different channels.
 */
public interface NotificationSender {

    /**
     * Send a notification using the given payload.
     *
     * @param payload the notification data
     * @throws Exception if the notification fails to send
     */
    void send(NotificationPayload payload) throws Exception;
}
