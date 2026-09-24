package com.medicare.patient.dto;

import java.time.LocalDate;

public record UpdatePatientProfileRequest(
        LocalDate dateOfBirth,
        String phoneNumber,
        String address
) {}