package com.medicare.patient.dto;

import java.util.UUID;

public record AllergyResponse(UUID id, String allergen, String notes) {}