package com.medicare.identity.repository;

import com.medicare.identity.entity.PasswordCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PasswordCredentialRepository extends JpaRepository<PasswordCredential, UUID> {
    Optional<PasswordCredential> findByUserId(UUID userId);
}