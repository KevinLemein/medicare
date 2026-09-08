package com.medicare.identity.controller;

import com.medicare.identity.common.response.ApiResponse;
import com.medicare.identity.common.response.ErrorDetail;
import com.medicare.identity.dto.request.PatientProvisionRequest;
import com.medicare.identity.dto.response.PatientProvisionResponse;
import com.medicare.identity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Called by patient-service via client_credentials, scope
 * identity:provision-patient. Never called by a human user — the caller's
 * client_id (from the JWT "sub" claim, which equals client_id for
 * client_credentials tokens) becomes actor_client_id on the resulting
 * audit event, not a human actor.
 */
@RestController
@RequestMapping("/internal/patients")
public class PatientProvisioningController {

    private final UserService userService;

    public PatientProvisioningController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/provision")
    public ResponseEntity<ApiResponse<PatientProvisionResponse>> provision(
            Authentication authentication,
            @Valid @RequestBody PatientProvisionRequest request) {

        String actorClientId = authentication.getName();

        var result = userService.provisionPatientAccount(
                request.idempotencyKey(), request.email(),
                request.firstName(), request.lastName(), actorClientId);

        return switch (result) {
            case UserService.ProvisioningResult.Created created -> ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success(
                            new PatientProvisionResponse("CREATED", created.userId()),
                            "Patient account created", null));
            case UserService.ProvisioningResult.Conflict ignored -> ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(
                            ErrorDetail.of("PATIENT_PROVISIONING_CONFLICT"),
                            "This request conflicts with an existing account or a prior request under a different idempotency key",
                            null));
        };
    }
}