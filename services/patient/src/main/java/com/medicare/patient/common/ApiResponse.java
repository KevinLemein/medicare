package com.medicare.patient.common;

import java.time.Instant;

// Standard envelope returned by every controller: { success, message, data, error, timestamp, requestId }
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        String error,
        Instant timestamp,
        String requestId
) {
    public static <T> ApiResponse<T> ok(String message, T data, String requestId) {
        return new ApiResponse<>(true, message, data, null, Instant.now(), requestId);
    }

    public static <T> ApiResponse<T> fail(String message, String error, String requestId) {
        return new ApiResponse<>(false, message, null, error, Instant.now(), requestId);
    }
}