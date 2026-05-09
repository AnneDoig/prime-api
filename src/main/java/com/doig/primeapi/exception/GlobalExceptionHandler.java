package com.doig.primeapi.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

// Central place to convert exceptions into a consistent API error response.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Handles invalid client input (e.g. negative upTo, bad algorithm values)
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
    // Handles invalid client input (e.g., negative upTo, bad algorithm values)
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