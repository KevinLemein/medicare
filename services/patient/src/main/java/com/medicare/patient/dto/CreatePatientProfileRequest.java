package com.medicare.patient.dto;

import java.time.LocalDate;

public record CreatePatientProfileRequest(
        LocalDate dateOfBirth,
        String phoneNumber,
        String address
) {}