package com.medicare.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Used for reactivateFromDeactivated, where UserService itself requires a
 * non-blank reason (§6) — enforced here too so validation fails at the DTO
 * layer with a clear message instead of falling through to the service.
 */
public record MandatoryReasonRequest(@NotBlank String reason) {}
