package com.medicare.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivationRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, message = "Password must be at least 12 characters long") String password
) {}
