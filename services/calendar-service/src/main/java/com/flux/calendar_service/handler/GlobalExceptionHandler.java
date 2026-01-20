package com.flux.calendar_service.handler;

import com.flux.calendar_service.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle ResourceNotFoundException (404)
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        log.error("Resource not found: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        errors.put("error", ex.getMessage());
        errors.put("path", request.getDescription(false).replace("uri=", ""));

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(errors));
    }

    /**
     * Handle EmptyFieldException (400)
     */
    @ExceptionHandler(EmptyFieldException.class)
    public ResponseEntity<ErrorResponse> handleEmptyFieldException(
            EmptyFieldException ex, WebRequest request) {
        log.error("Empty field validation failed: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        errors.put("error", ex.getMessage());
        errors.put("path", request.getDescription(false).replace("uri=", ""));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(errors));
    }

    /**
     * Handle InvalidTimeRangeException (400)
     */
    @ExceptionHandler(InvalidTimeRangeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTimeRangeException(
            InvalidTimeRangeException ex, WebRequest request) {
        log.error("Invalid time range: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        errors.put("error", ex.getMessage());
        errors.put("path", request.getDescription(false).replace("uri=", ""));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(errors));
    }

    /**
     * Handle DuplicateResourceException (409)
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResourceException(
            DuplicateResourceException ex, WebRequest request) {
        log.error("Duplicate resource: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        errors.put("error", ex.getMessage());
        errors.put("path", request.getDescription(false).replace("uri=", ""));

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(errors));
    }

    /**
     * Handle ConflictException (409)
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflictException(
            ConflictException ex, WebRequest request) {
        log.error("Resource conflict: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        errors.put("error", ex.getMessage());
        errors.put("path", request.getDescription(false).replace("uri=", ""));

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(errors));
    }

    /**
     * Handle ServiceUnavailableException (503)
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailableException(
            ServiceUnavailableException ex, WebRequest request) {
        log.error("Service unavailable: {}", ex.getMessage(), ex);

        Map<String, String> errors = new HashMap<>();
        errors.put("error", ex.getMessage());
        errors.put("path", request.getDescription(false).replace("uri=", ""));

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(errors));
    }

    /**
     * Handle FileOperationException (500)
     */
    @ExceptionHandler(FileOperationException.class)
    public ResponseEntity<ErrorResponse> handleFileOperationException(
            FileOperationException ex, WebRequest request) {
        log.error("File operation failed: {}", ex.getMessage(), ex);

        Map<String, String> errors = new HashMap<>();
        errors.put("error", ex.getMessage());
        errors.put("path", request.getDescription(false).replace("uri=", ""));

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(errors));
    }

    /**
     * Handle UnauthorizedException (401)
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(
            UnauthorizedException ex, WebRequest request) {
        log.error("Unauthorized access: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        errors.put("error", ex.getMessage());
        errors.put("path", request.getDescription(false).replace("uri=", ""));

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(errors));
    }

    /**
     * Handle validation errors from @Valid annotations (400)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        log.error("Validation failed: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(errors));
    }

    /**
     * Handle all other uncaught exceptions (500)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex, WebRequest request) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);

        Map<String, String> errors = new HashMap<>();
        errors.put("error", "An unexpected error occurred");
        errors.put("details", ex.getMessage());
        errors.put("path", request.getDescription(false).replace("uri=", ""));

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(errors));
    }
}
