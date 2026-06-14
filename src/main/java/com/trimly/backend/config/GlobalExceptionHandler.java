package com.trimly.backend.config;

import com.trimly.backend.dto.ErrorResponse;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.exception.ShopAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", java.time.Instant.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Failed");
        body.put("message", "One or more fields are invalid.");
        body.put("path", request.getRequestURI());
        body.put("fieldErrors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage(), request.getRequestURI())
        );
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleAuthFailure(
            Exception ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ErrorResponse.of(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", "Invalid email or password.", request.getRequestURI())
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", "Something went wrong. Please try again.", request.getRequestURI())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", "An unexpected error occurred.", request.getRequestURI())
        );
    }

    @ExceptionHandler(ShopAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleShopAccessDenied(
            ShopAccessDeniedException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ErrorResponse.of(HttpStatus.FORBIDDEN.value(), "Forbidden", ex.getMessage(), request.getRequestURI())
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage(), request.getRequestURI())
        );
    }

}
