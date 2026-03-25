package com.flux.calendar_service.handler;

import java.util.HashMap;
import java.util.Map;

import com.flux.calendar_service.exception.ConflictException;
import com.flux.calendar_service.exception.DuplicateResourceException;
import com.flux.calendar_service.exception.EmptyFieldException;
import com.flux.calendar_service.exception.FileOperationException;
import com.flux.calendar_service.exception.InvalidTimeRangeException;
import com.flux.calendar_service.exception.ResourceNotFoundException;
import com.flux.calendar_service.exception.ServiceUnavailableException;
import com.flux.calendar_service.exception.UnauthorizedException;

import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import lombok.extern.slf4j.Slf4j;

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
     * Handle GoogleTokenRevokedException (401 + machine-readable errorCode).
     *
     * <p>
     * This is the contract the frontend depends on to detect that a user's
     * Google OAuth refresh token has been permanently revoked (invalid_grant).
     *
     * <p>
     * The frontend should:
     * <ol>
     * <li>Intercept HTTP 401 responses where
     * {@code errorCode == "GOOGLE_REAUTH_REQUIRED"}</li>
     * <li>Show a banner / toast: "Google Calendar disconnected. Reconnect to resume
     * sync."</li>
     * <li>Provide a button that navigates to the {@code reconnectUrl} returned in
     * the body</li>
     * </ol>
     *
     * <p>
     * Response body example:
     * 
     * <pre>
     * HTTP 401 Unauthorized
     * {
     *   "errorCode":    "GOOGLE_REAUTH_REQUIRED",
     *   "message":      "Google Calendar access has been revoked. Please reconnect.",
     *   "reconnectUrl": "/auth/google?userId=abc123",
     *   "path":         "/calendar/abc/sync"
     * }
     * </pre>
     */
    @ExceptionHandler(com.flux.calendar_service.exception.GoogleTokenRevokedException.class)
    public ResponseEntity<Map<String, String>> handleGoogleTokenRevokedException(
            com.flux.calendar_service.exception.GoogleTokenRevokedException ex,
            WebRequest request) {
        log.warn("Google token revoked for user: {}. Returning GOOGLE_REAUTH_REQUIRED to client.", ex.getUserId());

        Map<String, String> body = new HashMap<>();
        body.put("errorCode", "GOOGLE_REAUTH_REQUIRED");
        body.put("message", "Google Calendar access has been revoked. Please reconnect your account.");
        body.put("reconnectUrl", "/auth/google?userId=" + ex.getUserId());
        body.put("path", request.getDescription(false).replace("uri=", ""));

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(body);
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
     * Handle request parameter type conversion errors (400)
     */
    @ExceptionHandler({ MethodArgumentTypeMismatchException.class, ConversionFailedException.class })
    public ResponseEntity<ErrorResponse> handleTypeConversionException(
            Exception ex, WebRequest request) {
        log.error("Request parameter conversion failed: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        if (ex instanceof MethodArgumentTypeMismatchException mismatchEx) {
            String parameterName = mismatchEx.getName();
            Object value = mismatchEx.getValue();
            errors.put(parameterName,
                    "Invalid value '" + value + "'. Expected ISO datetime format (e.g. 2026-02-24T10:30:00)");
        } else {
            errors.put("error", "Invalid request parameter format");
        }
        errors.put("path", request.getDescription(false).replace("uri=", ""));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(errors));
    }

    /**
     * Handle malformed JSON / request-body deserialization errors (400)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex, WebRequest request) {
        log.error("Request body parsing failed: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        errors.put("error",
                "Invalid request body format. For date-time fields use ISO-8601, e.g. 2026-03-09T22:30:00Z or 2026-03-09T22:30:00 (treated as UTC).");
        errors.put("path", request.getDescription(false).replace("uri=", ""));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(errors));
    }

    /**
     * Handle ResponseStatusException – preserves the HTTP status code set in the exception.
     */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            org.springframework.web.server.ResponseStatusException ex, WebRequest request) {
        log.warn("ResponseStatusException: {} – {}", ex.getStatusCode(), ex.getReason());

        Map<String, String> errors = new HashMap<>();
        errors.put("error", ex.getReason() != null ? ex.getReason() : "Bad Request");
        errors.put("path", request.getDescription(false).replace("uri=", ""));

        return ResponseEntity
                .status(ex.getStatusCode())
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

