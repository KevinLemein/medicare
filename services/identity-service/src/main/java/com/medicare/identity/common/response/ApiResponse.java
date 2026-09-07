package com.medicare.identity.common.response;

import java.time.Instant;

/**
 * Standard response envelope for every endpoint in this service.
 * Exactly one of {@code data} or {@code error} is populated, depending on
 * {@code success}.
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        ErrorDetail error,
        Instant timestamp,
        String requestId
) {

    public static <T> ApiResponse<T> success(T data, String message, String requestId) {
        return new ApiResponse<>(true, message, data, null, Instant.now(), requestId);
    }

    public static <T> ApiResponse<T> error(ErrorDetail error, String message, String requestId) {
        return new ApiResponse<>(false, message, null, error, Instant.now(), requestId);
    }
}