package com.medicare.identity.controller;

import com.medicare.identity.common.ApiResponse;
import com.medicare.identity.dto.AuthResponse;
import com.medicare.identity.dto.LoginRequest;
import com.medicare.identity.dto.RegisterRequest;
import com.medicare.identity.service.AuthService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse result = authService.register(request);
        return ResponseEntity.ok(ApiResponse.ok("Account created", result, MDC.get("requestId")));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse result = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", result, MDC.get("requestId")));
    }
}
