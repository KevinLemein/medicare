package com.medicare.identity.controller;

import com.medicare.identity.common.response.ApiResponse;
import com.medicare.identity.dto.request.AccountActionRequest;
import com.medicare.identity.dto.request.MandatoryReasonRequest;
import com.medicare.identity.dto.request.RoleChangeRequest;
import com.medicare.identity.dto.request.StaffAccountCreateRequest;
import com.medicare.identity.dto.response.UserResponse;
import com.medicare.identity.entity.User;
import com.medicare.identity.repository.UserRepository;
import com.medicare.identity.service.UserService;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin-only staff account lifecycle: creation, suspend/reactivate,
 * deactivate/reactivate, role changes, and lockout clearing. Restricted to
 * SYSTEM_ADMIN by SecurityConfig's "/admin/**" matcher.
 */
@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

    private final UserService userService;
    private final UserRepository userRepository;

    public AdminUserController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createStaffAccount(
            Authentication authentication,
            @Valid @RequestBody StaffAccountCreateRequest request) {

        UUID actorAdminId = resolveActorId(authentication);
        UserService.StaffAccountResult result = userService.createStaffAccount(
                request.email(), request.firstName(), request.lastName(), request.role(), actorAdminId);

        // Same convention as AdminBootstrapRunner: no notification service
        // exists yet, so the activation link is logged, never returned
        // over the API or emailed.
        log.info("Staff account created for {} ({}). Activation link: /accounts/activate?token={}",
                result.user().getEmail(), result.user().getRole(), result.rawActivationToken());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(UserResponse.from(result.user()), "Staff account created", null));
    }

    @PostMapping("/{userId}/suspend")
    public ResponseEntity<ApiResponse<UserResponse>> suspend(
            Authentication authentication, @PathVariable UUID userId,
            @RequestBody(required = false) AccountActionRequest request) {

        userService.suspend(userId, resolveActorId(authentication), reasonOf(request));
        return ResponseEntity.ok(ApiResponse.success(currentState(userId), "Account suspended", null));
    }

    @PostMapping("/{userId}/reactivate")
    public ResponseEntity<ApiResponse<UserResponse>> reactivate(
            Authentication authentication, @PathVariable UUID userId) {

        userService.reactivate(userId, resolveActorId(authentication));
        return ResponseEntity.ok(ApiResponse.success(currentState(userId), "Account reactivated", null));
    }

    @PostMapping("/{userId}/deactivate")
    public ResponseEntity<ApiResponse<UserResponse>> deactivate(
            Authentication authentication, @PathVariable UUID userId,
            @RequestBody(required = false) AccountActionRequest request) {

        userService.deactivate(userId, resolveActorId(authentication), reasonOf(request));
        return ResponseEntity.ok(ApiResponse.success(currentState(userId), "Account deactivated", null));
    }

    @PostMapping("/{userId}/reactivate-from-deactivated")
    public ResponseEntity<ApiResponse<UserResponse>> reactivateFromDeactivated(
            Authentication authentication, @PathVariable UUID userId,
            @Valid @RequestBody MandatoryReasonRequest request) {

        userService.reactivateFromDeactivated(userId, resolveActorId(authentication), request.reason());
        return ResponseEntity.ok(ApiResponse.success(currentState(userId), "Account reactivated", null));
    }

    @PostMapping("/{userId}/role")
    public ResponseEntity<ApiResponse<UserResponse>> changeRole(
            Authentication authentication, @PathVariable UUID userId,
            @Valid @RequestBody RoleChangeRequest request) {

        userService.changeRole(userId, resolveActorId(authentication), request.newRole());
        return ResponseEntity.ok(ApiResponse.success(currentState(userId), "Role changed", null));
    }

    @PostMapping("/{userId}/unlock")
    public ResponseEntity<ApiResponse<UserResponse>> unlock(
            Authentication authentication, @PathVariable UUID userId) {

        userService.unlockAccount(userId, resolveActorId(authentication));
        return ResponseEntity.ok(ApiResponse.success(currentState(userId), "Account unlocked", null));
    }

    private String reasonOf(AccountActionRequest request) {
        return request == null ? null : request.reason();
    }

    private UserResponse currentState(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));
        return UserResponse.from(user);
    }

    private UUID resolveActorId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"))
                .getId();
    }
}
