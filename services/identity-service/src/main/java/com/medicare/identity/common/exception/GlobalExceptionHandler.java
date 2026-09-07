package com.medicare.identity.common.exception;

import com.medicare.identity.common.response.ApiResponse;
import com.medicare.identity.common.response.ErrorDetail;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex, WebRequest request) {

        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .toList();

        ApiResponse<Void> body = ApiResponse.error(
                ErrorDetail.of("VALIDATION_FAILED", details),
                "Request validation failed",
                requestId(request)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            ResourceNotFoundException ex, WebRequest request) {

        ApiResponse<Void> body = ApiResponse.error(
                ErrorDetail.of("RESOURCE_NOT_FOUND"),
                ex.getMessage(),
                requestId(request)
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // Covers UserService's token/validation failures — invalid, expired,
    // or already-used activation/reset tokens; a weak password.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {

        ApiResponse<Void> body = ApiResponse.error(
                ErrorDetail.of("INVALID_REQUEST"),
                ex.getMessage(),
                requestId(request)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // Covers UserService's business-rule violations — wrong current
    // password, an account not in the expected status for a given
    // transition (e.g. reactivating something that isn't DEACTIVATED).
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(
            IllegalStateException ex, WebRequest request) {

        ApiResponse<Void> body = ApiResponse.error(
                ErrorDetail.of("INVALID_STATE"),
                ex.getMessage(),
                requestId(request)
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception ex, WebRequest request) {

        ApiResponse<Void> body = ApiResponse.error(
                ErrorDetail.of("INTERNAL_ERROR"),
                "An unexpected error occurred",
                requestId(request)
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private String formatFieldError(FieldError fieldError) {
        return "%s: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private String requestId(WebRequest request) {
        return request.getHeader("X-Request-Id");
    }
}