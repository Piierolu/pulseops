package com.pulseops.controlplane.config;

import com.pulseops.controlplane.monitor.InvalidTargetException;
import com.pulseops.controlplane.monitor.MonitorNotFoundException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(MonitorNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(MonitorNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("monitor_not_found", exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(InvalidTargetException.class)
    ResponseEntity<ApiError> handleInvalidTarget(InvalidTargetException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("invalid_target", exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = exception.getBindingResult().getFieldErrors().stream()
                .collect(java.util.stream.Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() == null ? "invalid value" : error.getDefaultMessage(),
                        (first, ignored) -> first
                ));
        return ResponseEntity.badRequest().body(Map.of(
                "code", "validation_failed",
                "message", "Request validation failed",
                "timestamp", Instant.now(),
                "fields", fields
        ));
    }

    record ApiError(String code, String message, Instant timestamp) {
    }
}
