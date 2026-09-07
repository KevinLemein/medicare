package com.medicare.identity.controller;

import com.medicare.identity.common.response.ApiResponse;
import com.medicare.identity.dto.request.PasswordResetConfirmRequest;
import com.medicare.identity.dto.request.PasswordResetRequest;
import com.medicare.identity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts/password-reset")
public class PasswordController {

    private final UserService userService;

    public PasswordController(UserService userService) {
        this.userService = userService;
    }

    // §16/§20: same response whether or not the email exists — the
    // returned Optional from requestPasswordReset is deliberately unused.
    @PostMapping("/request")
    public ResponseEntity<ApiResponse<Void>> requestReset(@Valid @RequestBody PasswordResetRequest request) {
        userService.requestPasswordReset(request.email());
        return ResponseEntity.ok(ApiResponse.success(null,
                "If an account exists for this email, a reset link has been sent", null));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        userService.completePasswordReset(request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.success(null, "Password has been reset", null));
    }
}