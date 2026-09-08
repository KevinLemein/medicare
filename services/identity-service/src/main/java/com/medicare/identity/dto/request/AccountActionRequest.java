package com.medicare.identity.dto.request;

/**
 * Shared shape for suspend/deactivate — {@code reason} is optional at the
 * DTO layer, matching UserService.suspend/deactivate which don't require
 * one. Use {@link MandatoryReasonRequest} where the service layer does.
 */
public record AccountActionRequest(String reason) {}
