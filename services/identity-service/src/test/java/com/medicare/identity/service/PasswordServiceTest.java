package com.medicare.identity.service;

import com.medicare.identity.entity.PasswordCredential;
import com.medicare.identity.repository.PasswordCredentialRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    private final PasswordEncoder passwordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Mock
    private PasswordCredentialRepository credentialRepository;

    private PasswordService passwordService;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService(credentialRepository, passwordEncoder);
    }

    @Test
    void validateStrength_rejectsPasswordShorterThanTwelveCharacters() {
        assertThatThrownBy(() -> passwordService.validateStrength("short11"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("12 characters");
    }

    @Test
    void validateStrength_rejectsNullPassword() {
        assertThatThrownBy(() -> passwordService.validateStrength(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateStrength_acceptsPasswordAtLeastTwelveCharacters() {
        passwordService.validateStrength("twelvecharspw");
    }

    @Test
    void setInitialPassword_hashesRatherThanStoringPlaintext() {
        UUID userId = UUID.randomUUID();
        ArgumentCaptor<PasswordCredential> captor = ArgumentCaptor.forClass(PasswordCredential.class);

        passwordService.setInitialPassword(userId, "correct-horse-battery");

        verify(credentialRepository).save(captor.capture());
        PasswordCredential saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getPasswordHash()).isNotEqualTo("correct-horse-battery");
        assertThat(passwordEncoder.matches("correct-horse-battery", saved.getPasswordHash())).isTrue();
    }

    @Test
    void setInitialPassword_rejectsWeakPasswordWithoutTouchingRepository() {
        assertThatThrownBy(() -> passwordService.setInitialPassword(UUID.randomUUID(), "tooshort"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(credentialRepository);
    }

    @Test
    void changePassword_updatesExistingCredential() {
        UUID userId = UUID.randomUUID();
        PasswordCredential existing = new PasswordCredential();
        existing.setUserId(userId);
        existing.setPasswordHash(passwordEncoder.encode("old-password-value"));
        when(credentialRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        passwordService.changePassword(userId, "brand-new-password");

        assertThat(passwordEncoder.matches("brand-new-password", existing.getPasswordHash())).isTrue();
        verify(credentialRepository).save(existing);
    }

    @Test
    void changePassword_throwsWhenNoCredentialExists() {
        UUID userId = UUID.randomUUID();
        when(credentialRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordService.changePassword(userId, "brand-new-password"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void matches_returnsTrueForCorrectPassword() {
        UUID userId = UUID.randomUUID();
        PasswordCredential credential = new PasswordCredential();
        credential.setUserId(userId);
        credential.setPasswordHash(passwordEncoder.encode("the-real-password"));
        when(credentialRepository.findByUserId(userId)).thenReturn(Optional.of(credential));

        assertThat(passwordService.matches(userId, "the-real-password")).isTrue();
        assertThat(passwordService.matches(userId, "wrong-password")).isFalse();
    }

    @Test
    void matches_returnsFalseWhenNoCredentialExists() {
        UUID userId = UUID.randomUUID();
        when(credentialRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThat(passwordService.matches(userId, "anything")).isFalse();
    }
}
