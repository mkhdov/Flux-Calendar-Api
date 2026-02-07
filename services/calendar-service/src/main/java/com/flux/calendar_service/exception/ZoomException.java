package com.flux.calendar_service.exception;

public class ZoomException extends RuntimeException {
    public ZoomException(String message) {
        super(message);
    }

    public ZoomException(String message, Throwable cause) {
        super(message, cause);
    }
}
