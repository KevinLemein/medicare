package com.medicare.identity.repository;

import com.medicare.identity.entity.AccountToken;
import com.medicare.identity.entity.AccountTokenType;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountTokenRepository extends JpaRepository<AccountToken, UUID> {
    Optional<AccountToken> findByTokenHash(String tokenHash);
    List<AccountToken> findByUserIdAndTypeAndUsedAtIsNull(UUID userId, AccountTokenType type);
}
