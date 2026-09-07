package com.medicare.identity.service;

import com.medicare.identity.entity.PasswordCredential;
import com.medicare.identity.repository.PasswordCredentialRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Argon2id hashing/verification (§13). Deliberately the only class that
 * touches password_hash directly — nowhere else constructs or compares one.
 */
@Service
public class PasswordService {

    // NIST SP 800-63B: minimum length over complexity rules (§13).
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final PasswordCredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordService(PasswordCredentialRepository credentialRepository, PasswordEncoder passwordEncoder) {
        this.credentialRepository = credentialRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void validateStrength(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters long");
        }
        // Breach-list checking (§13) is a flagged nice-to-have, not a v1
        // requirement — not implemented here.
    }

    public void setInitialPassword(UUID userId, String rawPassword) {
        validateStrength(rawPassword);
        PasswordCredential credential = new PasswordCredential();
        credential.setUserId(userId);
        credential.setPasswordHash(passwordEncoder.encode(rawPassword));
        credentialRepository.save(credential);
    }

    public void changePassword(UUID userId, String newRawPassword) {
        validateStrength(newRawPassword);
        PasswordCredential credential = credentialRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("No credential exists for user " + userId));
        credential.setPasswordHash(passwordEncoder.encode(newRawPassword));
        credential.setUpdatedAt(Instant.now());
        credentialRepository.save(credential);
    }

    public boolean matches(UUID userId, String rawPassword) {
        return credentialRepository.findByUserId(userId)
                .map(c -> passwordEncoder.matches(rawPassword, c.getPasswordHash()))
                .orElse(false);
    }
}