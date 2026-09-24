package com.medicare.patient.controller;

import com.medicare.patient.common.ApiResponse;
import com.medicare.patient.dto.*;
import com.medicare.patient.service.PatientService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @PostMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<PatientProfileResponse>> createProfile(
            @Valid @RequestBody CreatePatientProfileRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        PatientProfileResponse result = patientService.createProfile(request, userId);
        return ResponseEntity.ok(ApiResponse.ok("Patient profile created", result, MDC.get("requestId")));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<PatientProfileResponse>> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        PatientProfileResponse result = patientService.getMyProfile(userId);
        return ResponseEntity.ok(ApiResponse.ok("Patient profile retrieved", result, MDC.get("requestId")));
    }

    @PostMapping("/me/emergency-contacts")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<EmergencyContactResponse>> addEmergencyContact(
            @Valid @RequestBody EmergencyContactRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        var result = patientService.addEmergencyContact(request, userId);
        return ResponseEntity.ok(ApiResponse.ok("Emergency contact added", result, MDC.get("requestId")));
    }

    @DeleteMapping("/me/emergency-contacts/{contactId}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<Void>> removeEmergencyContact(
            @PathVariable UUID contactId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        patientService.removeEmergencyContact(contactId, userId);
        return ResponseEntity.ok(ApiResponse.ok("Emergency contact removed", null, MDC.get("requestId")));
    }

    @PostMapping("/me/allergies")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<AllergyResponse>> addAllergy(
            @Valid @RequestBody AllergyRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        var result = patientService.addAllergy(request, userId);
        return ResponseEntity.ok(ApiResponse.ok("Allergy added", result, MDC.get("requestId")));
    }

    @DeleteMapping("/me/allergies/{allergyId}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<Void>> removeAllergy(
            @PathVariable UUID allergyId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        patientService.removeAllergy(allergyId, userId);
        return ResponseEntity.ok(ApiResponse.ok("Allergy removed", null, MDC.get("requestId")));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<PatientProfileResponse>> updateProfile(
            @Valid @RequestBody UpdatePatientProfileRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        var result = patientService.updateProfile(request, userId);
        return ResponseEntity.ok(ApiResponse.ok("Patient profile updated", result, MDC.get("requestId")));
    }
}