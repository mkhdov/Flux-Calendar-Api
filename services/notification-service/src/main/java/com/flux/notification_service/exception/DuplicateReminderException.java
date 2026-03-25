package com.flux.notification_service.exception;

public class DuplicateReminderException extends RuntimeException {
    public DuplicateReminderException(String message) {
        super(message);
    }
}
