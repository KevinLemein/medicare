package com.medicare.patient.dto;

import com.medicare.patient.entity.RelationshipType;
import java.util.UUID;

public record EmergencyContactResponse(UUID id, String name, String phone, RelationshipType relationshipType) {}