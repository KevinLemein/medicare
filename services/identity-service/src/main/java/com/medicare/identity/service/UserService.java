package com.medicare.identity.service;

import com.medicare.identity.entity.*;
import com.medicare.identity.repository.*;
import com.medicare.identity.util.TokenGenerator;


import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;


import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import java.util.Map;
import java.util.Optional;

@Service

public class UserService {

    private static final int LOCKOUT_MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MINUTES = 15;
    private static final long ACTIVATION_TOKEN_TTL_HOURS = 48;
    private static final long RESET_TOKEN_TTL_HOURS = 1;

    private final UserRepository userRepository;
    private final AccountTokenRepository accountTokenRepository;
    private final AccountProvisioningRequestRepository provisioningRequestRepository;
    private final PasswordService passwordService;
    private final AuditEventService auditEventService;
    private final TokenGenerator tokenGenerator;

    public UserService(UserRepository userRepository,
                       AccountTokenRepository accountTokenRepository,
                       AccountProvisioningRequestRepository provisioningRequestRepository,
                       PasswordService passwordService,
                       AuditEventService auditEventService,
                       TokenGenerator tokenGenerator) {
        this.userRepository = userRepository;
        this.accountTokenRepository = accountTokenRepository;
        this.provisioningRequestRepository = provisioningRequestRepository;
        this.passwordService = passwordService;
        this.auditEventService = auditEventService;
        this.tokenGenerator = tokenGenerator;
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // ---- Workflow A: staff account creation ----

    public record StaffAccountResult(User user, String rawActivationToken) {}

    @Transactional
    public StaffAccountResult createStaffAccount(String email, String firstName, String lastName,
                                                 Role role, UUID actorAdminId) {
        if (role == Role.PATIENT) {
            throw new IllegalArgumentException(
                    "PATIENT accounts are provisioned by Patient Service, not created here");
        }
        String normalizedEmail = normalize(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalStateException("A user with this email already exists");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole(role);
        user.setStatus(AccountStatus.PENDING_ACTIVATION);
        userRepository.save(user);

        String rawToken = issueAccountToken(user.getId(), AccountTokenType.ACTIVATION, ACTIVATION_TOKEN_TTL_HOURS);
        auditEventService.recordByUser(AuditEventType.ACCOUNT_CREATED, actorAdminId, user.getId(),
                Map.of("role", role.name()));

        return new StaffAccountResult(user, rawToken);
    }

    // ---- Category C: patient provisioning (§7/§14/§20 Workflow B/C) ----

    public sealed interface ProvisioningResult permits ProvisioningResult.Created, ProvisioningResult.Conflict {
        record Created(UUID userId) implements ProvisioningResult {}
        record Conflict() implements ProvisioningResult {}
    }

    @Transactional
    public ProvisioningResult provisionPatientAccount(UUID idempotencyKey, String email,
                                                      String firstName, String lastName) {
        Optional<AccountProvisioningRequest> existing = provisioningRequestRepository.findById(idempotencyKey);
        if (existing.isPresent()) {
            AccountProvisioningRequest previous = existing.get();
            return previous.getOutcome() == ProvisioningOutcome.CREATED
                    ? new ProvisioningResult.Created(previous.getUserId())
                    : new ProvisioningResult.Conflict();
        }

        String normalizedEmail = normalize(email);
        AccountProvisioningRequest request = new AccountProvisioningRequest();
        request.setIdempotencyKey(idempotencyKey);
        request.setRequestedEmail(normalizedEmail);

        if (userRepository.existsByEmail(normalizedEmail)) {
            request.setOutcome(ProvisioningOutcome.CONFLICT);
            provisioningRequestRepository.save(request);
            auditEventService.recordSystem(AuditEventType.PATIENT_PROVISIONING_CONFLICT, null,
                    Map.of("requestedEmail", normalizedEmail));
            return new ProvisioningResult.Conflict();
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole(Role.PATIENT);
        user.setStatus(AccountStatus.PENDING_ACTIVATION);
        userRepository.save(user);

        request.setOutcome(ProvisioningOutcome.CREATED);
        request.setUserId(user.getId());
        provisioningRequestRepository.save(request);

        auditEventService.recordSystem(AuditEventType.ACCOUNT_CREATED, user.getId(),
                Map.of("role", Role.PATIENT.name()));

        return new ProvisioningResult.Created(user.getId());
    }

    // ---- Activation (tail of Workflow A / B) ----

    @Transactional
    public void activateAccount(String rawToken, String newPassword) {
        AccountToken token = findValidToken(rawToken, AccountTokenType.ACTIVATION);
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalStateException("Token references a missing user"));

        if (user.getStatus() != AccountStatus.PENDING_ACTIVATION) {
            throw new IllegalStateException("Account is not pending activation");
        }

        passwordService.setInitialPassword(user.getId(), newPassword);
        token.setUsedAt(Instant.now());
        accountTokenRepository.save(token);

        user.setStatus(AccountStatus.ACTIVE);
        userRepository.save(user);

        auditEventService.recordSystem(AuditEventType.ACCOUNT_ACTIVATED, user.getId(), Map.of());
    }

    // ---- Workflow H: login outcome / lockout gate ----

    @Transactional
    public void recordSuccessfulLogin(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        auditEventService.recordSystem(AuditEventType.LOGIN_SUCCESS, userId, Map.of());
    }

    public void checkActivationToken(String rawToken) {
        findValidToken(rawToken, AccountTokenType.ACTIVATION);
    }

    @Transactional
    public void recordFailedLoginAttempt(String email) {
        String normalizedEmail = normalize(email);
        Optional<User> maybeUser = userRepository.findByEmail(normalizedEmail);

        // §16: unlike a reset request against an unknown email, a failed
        // login IS logged even with no matching account — genuine
        // credential-stuffing signal, and there's no account to protect.
        if (maybeUser.isEmpty()) {
            auditEventService.recordSystem(AuditEventType.LOGIN_FAILED, null, Map.of());
            return;
        }

        User user = maybeUser.get();
        user.setFailedLoginCount(user.getFailedLoginCount() + 1);

        if (user.getFailedLoginCount() >= LOCKOUT_MAX_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION_MINUTES, ChronoUnit.MINUTES));
            userRepository.save(user);
            auditEventService.recordSystem(AuditEventType.LOGIN_LOCKOUT_TRIGGERED, user.getId(), Map.of());
        } else {
            userRepository.save(user);
        }
        auditEventService.recordSystem(AuditEventType.LOGIN_FAILED, user.getId(), Map.of());
    }


    // ---- Workflow F: password reset ----

    @Transactional
    public Optional<String> requestPasswordReset(String email) {
        Optional<User> maybeUser = userRepository.findByEmail(normalize(email));

        // §16: no audit event, no token, nothing observable for an unknown
        // email — this is the enumeration-safety rule that's the opposite
        // of the LOGIN_FAILED case above.
        if (maybeUser.isEmpty()) {
            return Optional.empty();
        }
        User user = maybeUser.get();

        accountTokenRepository.findByUserIdAndTypeAndUsedAtIsNull(user.getId(), AccountTokenType.PASSWORD_RESET)
                .forEach(old -> {
                    old.setUsedAt(Instant.now());
                    accountTokenRepository.save(old);
                });

        String rawToken = issueAccountToken(user.getId(), AccountTokenType.PASSWORD_RESET, RESET_TOKEN_TTL_HOURS);
        auditEventService.recordSystem(AuditEventType.PASSWORD_RESET_REQUESTED, user.getId(), Map.of());
        return Optional.of(rawToken);
    }

    @Transactional
    public void completePasswordReset(String rawToken, String newPassword) {
        AccountToken token = findValidToken(rawToken, AccountTokenType.PASSWORD_RESET);
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalStateException("Token references a missing user"));

        passwordService.changePassword(user.getId(), newPassword);
        token.setUsedAt(Instant.now());
        accountTokenRepository.save(token);

        // §20 Workflow F: a successful reset also clears the lockout gate —
        // strong evidence the legitimate owner is back in control.
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        auditEventService.recordSystem(AuditEventType.PASSWORD_RESET_COMPLETED, user.getId(), Map.of());

        // NOT done here: refresh-token/authorization revocation (§12/§13).
        // That needs Spring Authorization Server's OAuth2AuthorizationService,
        // which doesn't exist until AuthorizationServerConfig is built.
    }

    // ---- Workflow G: authenticated password change ----

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        if (!passwordService.matches(userId, currentPassword)) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        passwordService.changePassword(userId, newPassword);
        auditEventService.recordByUser(AuditEventType.PASSWORD_CHANGED, userId, userId, Map.of());
        // Same revocation caveat as completePasswordReset.
    }

    // ---- Workflows I/J/K: admin actions ----

    @Transactional
    public void suspend(UUID userId, UUID actorAdminId, String reason) {
        User user = userRepository.findById(userId).orElseThrow();
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Only an ACTIVE account can be suspended");
        }
        user.setStatus(AccountStatus.SUSPENDED);
        userRepository.save(user);
        auditEventService.recordByUser(AuditEventType.ACCOUNT_SUSPENDED, actorAdminId, userId, Map.of("reason", reason));
    }

    @Transactional
    public void reactivate(UUID userId, UUID actorAdminId) {
        User user = userRepository.findById(userId).orElseThrow();
        if (user.getStatus() != AccountStatus.SUSPENDED) {
            throw new IllegalStateException("Only a SUSPENDED account can be reactivated this way");
        }
        user.setStatus(AccountStatus.ACTIVE);
        userRepository.save(user);
        auditEventService.recordByUser(AuditEventType.ACCOUNT_REACTIVATED, actorAdminId, userId, Map.of());
    }

    @Transactional
    public void deactivate(UUID userId, UUID actorAdminId, String reason) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setStatus(AccountStatus.DEACTIVATED);
        userRepository.save(user);
        auditEventService.recordByUser(AuditEventType.ACCOUNT_DEACTIVATED, actorAdminId, userId, Map.of("reason", reason));
    }

    @Transactional
    public void reactivateFromDeactivated(UUID userId, UUID actorAdminId, String reason) {
        User user = userRepository.findById(userId).orElseThrow();
        if (user.getStatus() != AccountStatus.DEACTIVATED) {
            throw new IllegalStateException("Account is not DEACTIVATED");
        }
        if (reason == null || reason.isBlank()) {
            // §6: this specific transition requires a mandatory reason —
            // enforced here, not left to the controller layer.
            throw new IllegalArgumentException("A reason is required to reactivate a deactivated account");
        }
        user.setStatus(AccountStatus.ACTIVE);
        userRepository.save(user);
        auditEventService.recordByUser(AuditEventType.ACCOUNT_REACTIVATED_FROM_DEACTIVATED, actorAdminId, userId, Map.of("reason", reason));
    }

    @Transactional
    public void changeRole(UUID userId, UUID actorAdminId, Role newRole) {
        User user = userRepository.findById(userId).orElseThrow();
        Role oldRole = user.getRole();
        user.setRole(newRole);
        userRepository.save(user);
        auditEventService.recordByUser(AuditEventType.ROLE_CHANGED, actorAdminId, userId,
                Map.of("oldRole", oldRole.name(), "newRole", newRole.name()));
        // Same revocation caveat as password change/reset above.
    }

    @Transactional
    public void unlockAccount(UUID userId, UUID actorAdminId) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        auditEventService.recordByUser(AuditEventType.LOGIN_LOCKOUT_CLEARED, actorAdminId, userId, Map.of());
    }

    // ---- Helpers ----

    private String issueAccountToken(UUID userId, AccountTokenType type, long ttlHours) {
        TokenGenerator.GeneratedToken generated = tokenGenerator.generate();
        AccountToken token = new AccountToken();
        token.setUserId(userId);
        token.setType(type);
        token.setTokenHash(generated.hash());
        token.setExpiresAt(Instant.now().plus(ttlHours, ChronoUnit.HOURS));
        accountTokenRepository.save(token);
        return generated.rawValue();
    }

    private AccountToken findValidToken(String rawToken, AccountTokenType expectedType) {
        AccountToken token = accountTokenRepository.findByTokenHash(tokenGenerator.hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));
        if (token.getType() != expectedType) {
            throw new IllegalArgumentException("Invalid token");
        }
        if (token.getUsedAt() != null) {
            throw new IllegalArgumentException("This link has already been used");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("This link has expired");
        }
        return token;
    }
}