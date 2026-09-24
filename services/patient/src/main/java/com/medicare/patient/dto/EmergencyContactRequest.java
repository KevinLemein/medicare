package com.medicare.patient.dto;

import com.medicare.patient.entity.RelationshipType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EmergencyContactRequest(
        @NotBlank String name,
        @NotBlank String phone,
        @NotNull RelationshipType relationshipType
) {}