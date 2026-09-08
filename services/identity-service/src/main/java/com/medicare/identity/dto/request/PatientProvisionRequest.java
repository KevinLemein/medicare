package com.medicare.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PatientProvisionRequest(
        @NotNull UUID idempotencyKey,
        @NotBlank @Email String email,
        @NotBlank String firstName,
        @NotBlank String lastName
) {}
