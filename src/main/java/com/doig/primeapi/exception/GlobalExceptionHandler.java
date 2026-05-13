package com.doig.primeapi.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler for consistent error responses across the API.
 *
 * All caught exceptions are converted to a standardized JSON/XML error response with:
 * - timestamp: when the error occurred
 * - status: HTTP status code
 * - error: HTTP status reason phrase
 * - message: user-friendly error message
 * - path: request URI that caused the error
 *
 * Handles three exception types:
 * 1. IllegalArgumentException: validation errors from PrimeService logic
 * 2. ResponseStatusException: business rule violations
 * 3. MethodArgumentTypeMismatchException: query parameter type conversion failures
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Handles validation errors from service layer (e.g. negative upTo, invalid page/size).
    // Invoked by Spring at runtime via @ExceptionHandler (may appear unused in IDE).
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        return buildError(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    // Handles business rule violations from service layer (e.g. upTo exceeds configured cap).
    // Invoked by Spring at runtime via @ExceptionHandler (may appear unused in IDE).
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() != null ? ex.getReason() : "Request failed";
        return buildError(status, message, request.getRequestURI());
    }

    // Handles query parameter type conversion failures (e.g. upTo=abc for an int parameter).
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        String parameter = ex.getName();
        String expectedType = ex.getRequiredType() != null
                ? ex.getRequiredType().getSimpleName()
                : "required type";
        String message = "Invalid value for parameter '" + parameter + "'. Expected " + expectedType + ".";
        return buildError(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    // Builds the shared response shape used for all handled errors.
    private ResponseEntity<Map<String, Object>> buildError(
            HttpStatus status,
            String message,
            String path
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", path);

        return ResponseEntity.status(status).body(body);
    }
}