package com.medicare.patient.dto;

import jakarta.validation.constraints.NotBlank;

public record AllergyRequest(
        @NotBlank String allergen,
        String notes
) {}