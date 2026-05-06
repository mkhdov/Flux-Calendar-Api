package com.flux.user_management_service.exception;

public class RepeatedPasswordNotMatchException extends RuntimeException {
    public RepeatedPasswordNotMatchException(String message) {
        super(message);
    }
}
