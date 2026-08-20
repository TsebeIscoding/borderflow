package com.borderflow.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TripNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(TripNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(InvalidHandoverException.class)
    public ResponseEntity<Map<String, Object>> handleInvalid(InvalidHandoverException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(HttpStatus.BAD_REQUEST, message));
    }

    private Map<String, Object> body(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}
