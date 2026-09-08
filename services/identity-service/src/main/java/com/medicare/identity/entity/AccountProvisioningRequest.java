package com.medicare.identity.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_provisioning_requests")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AccountProvisioningRequest {

    // The idempotency key IS the primary key — supplied by Patient
    // Service, not generated here. No @GeneratedValue.
    @Id
    @EqualsAndHashCode.Include
    @Column(name = "idempotency_key")
    private UUID idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProvisioningOutcome outcome;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "requested_email", nullable = false, length = 320)
    private String requestedEmail;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
