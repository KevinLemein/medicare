package com.medicare.identity.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Flat page shape for API responses — deliberately not Spring Data's
 * {@code Page}/{@code PageImpl} directly, whose default Jackson
 * serialization is unstable across versions and exposes internals
 * (pageable, sort) callers shouldn't depend on.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
