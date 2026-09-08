package com.medicare.identity.service;

import com.medicare.identity.entity.AuditEventType;
import com.medicare.identity.entity.User;
import com.medicare.identity.repository.*;
import com.medicare.identity.util.TokenGenerator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceLockoutTest {

    private static final int LOCKOUT_MAX_ATTEMPTS = 5;

    @Mock private UserRepository userRepository;
    @Mock private AccountTokenRepository accountTokenRepository;
    @Mock private AccountProvisioningRequestRepository provisioningRequestRepository;
    @Mock private PasswordService passwordService;
    @Mock private AuditEventService auditEventService;
    @Mock private TokenGenerator tokenGenerator;
    @Mock private PlatformTransactionManager transactionManager;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, accountTokenRepository, provisioningRequestRepository,
                passwordService, auditEventService, tokenGenerator, transactionManager);
    }

    private User activeUser(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setFailedLoginCount(0);
        return user;
    }

    @Test
    void recordFailedLoginAttempt_belowThreshold_incrementsWithoutLocking() {
        User user = activeUser("nurse@test.local");
        user.setFailedLoginCount(2);
        when(userRepository.findByEmail("nurse@test.local")).thenReturn(Optional.of(user));

        userService.recordFailedLoginAttempt("nurse@test.local");

        assertThat(user.getFailedLoginCount()).isEqualTo(3);
        assertThat(user.getLockedUntil()).isNull();
        verify(auditEventService).recordSystem(AuditEventType.LOGIN_FAILED, user.getId(), Map.of());
        verify(auditEventService, never()).recordSystem(eq(AuditEventType.LOGIN_LOCKOUT_TRIGGERED), any(), any());
    }

    @Test
    void recordFailedLoginAttempt_hittingThreshold_locksAccountAndAudits() {
        User user = activeUser("doctor@test.local");
        user.setFailedLoginCount(LOCKOUT_MAX_ATTEMPTS - 1);
        when(userRepository.findByEmail("doctor@test.local")).thenReturn(Optional.of(user));

        userService.recordFailedLoginAttempt("doctor@test.local");

        assertThat(user.getFailedLoginCount()).isEqualTo(LOCKOUT_MAX_ATTEMPTS);
        assertThat(user.getLockedUntil()).isNotNull().isAfter(Instant.now());
        verify(auditEventService).recordSystem(AuditEventType.LOGIN_LOCKOUT_TRIGGERED, user.getId(), Map.of());
        verify(auditEventService).recordSystem(AuditEventType.LOGIN_FAILED, user.getId(), Map.of());
    }

    @Test
    void recordFailedLoginAttempt_unknownEmail_auditsWithNullSubjectAndTouchesNoUser() {
        when(userRepository.findByEmail("nobody@test.local")).thenReturn(Optional.empty());

        userService.recordFailedLoginAttempt("nobody@test.local");

        verify(auditEventService).recordSystem(AuditEventType.LOGIN_FAILED, null, Map.of());
        verify(userRepository, never()).save(any());
    }

    @Test
    void recordFailedLoginAttempt_normalizesEmailBeforeLookup() {
        User user = activeUser("mixed@test.local");
        when(userRepository.findByEmail("mixed@test.local")).thenReturn(Optional.of(user));

        userService.recordFailedLoginAttempt("  Mixed@Test.Local  ");

        verify(userRepository).findByEmail("mixed@test.local");
    }

    @Test
    void recordSuccessfulLogin_resetsCounterAndClearsLockout() {
        User user = activeUser("recovered@test.local");
        user.setFailedLoginCount(4);
        user.setLockedUntil(Instant.now().plusSeconds(60));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.recordSuccessfulLogin(user.getId());

        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(auditEventService).recordSystem(AuditEventType.LOGIN_SUCCESS, user.getId(), Map.of());
    }
}
