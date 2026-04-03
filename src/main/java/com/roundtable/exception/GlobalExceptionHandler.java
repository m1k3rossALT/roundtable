package com.roundtable.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception handling for all REST controllers.
 *
 * Every error returns a consistent JSON structure:
 * {
 *   "timestamp": "...",
 *   "status":    400,
 *   "error":     "Bad Request",
 *   "message":   "Human-readable description"
 * }
 *
 * Never exposes stack traces or internal details to the client.
 * Full detail always logged server-side.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        FieldError::getDefaultMessage,
                        (e, r) -> e));

        return build(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
    }

    @ExceptionHandler(ProviderException.class)
    public ResponseEntity<Map<String, Object>> handleProvider(ProviderException ex) {
        log.warn("Provider error [{}]: {}", ex.getProvider(), ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY,
                "Provider error (" + ex.getProvider() + "): " + ex.getMessage(), null);
    }

    @ExceptionHandler(ModuleException.class)
    public ResponseEntity<Map<String, Object>> handleModule(ModuleException ex) {
        log.error("Module error [{}]: {}", ex.getModuleId(), ex.getMessage());
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Module error: " + ex.getMessage(), null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Check server logs.", null);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status,
                                                       String message,
                                                       Object details) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status",    status.value());
        body.put("error",     status.getReasonPhrase());
        body.put("message",   message);
        if (details != null) body.put("details", details);
        return ResponseEntity.status(status).body(body);
    }
}
