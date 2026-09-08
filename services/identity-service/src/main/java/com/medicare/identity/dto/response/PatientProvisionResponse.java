package com.medicare.identity.dto.response;

import java.util.UUID;

public record PatientProvisionResponse(String outcome, UUID userId) {}