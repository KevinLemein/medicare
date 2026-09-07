package com.medicare.identity.controller;

import com.medicare.identity.common.response.ApiResponse;
import com.medicare.identity.dto.request.ActivationRequest;
import com.medicare.identity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts/activate")
public class ActivationController {

    private final UserService userService;

    public ActivationController(UserService userService) {
        this.userService = userService;
    }

    // Lets a future frontend validate a token before showing a
    // "set your password" form, without consuming it.
    @GetMapping
    public ResponseEntity<ApiResponse<Void>> checkToken(@RequestParam String token) {
        userService.checkActivationToken(token);
        return ResponseEntity.ok(ApiResponse.success(null, "Token is valid", null));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> activate(@Valid @RequestBody ActivationRequest request) {
        userService.activateAccount(request.token(), request.password());
        return ResponseEntity.ok(ApiResponse.success(null, "Account activated", null));
    }
}