package com.medicare.patient.dto;

import com.medicare.patient.entity.RelationshipType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PatientProfileResponse(
        UUID id,
        LocalDate dateOfBirth,
        String phoneNumber,
        String address,
        List<EmergencyContactView> emergencyContacts,
        List<AllergyView> allergies
) {
    public record EmergencyContactView(UUID id, String name, String phone, RelationshipType relationshipType) {}
    public record AllergyView(UUID id, String allergen, String notes) {}
}