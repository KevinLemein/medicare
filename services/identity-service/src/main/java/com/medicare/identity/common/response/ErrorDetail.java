package com.medicare.identity.common.response;

import java.util.List;

/**
 * Structured error payload nested inside {@link ApiResponse} on failure.
 */
public record ErrorDetail(String code, List<String> details) {

    public static ErrorDetail of(String code) {
        return new ErrorDetail(code, List.of());
    }

    public static ErrorDetail of(String code, List<String> details) {
        return new ErrorDetail(code, details);
    }
}