package com.flux.user_management_service.exception;

public class FieldIsEmptyValidatorException extends RuntimeException {
    public FieldIsEmptyValidatorException(String message) {
        super(message);
    }
}
