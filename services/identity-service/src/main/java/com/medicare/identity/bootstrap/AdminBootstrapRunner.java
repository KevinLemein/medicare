package com.medicare.identity.bootstrap;

import com.medicare.identity.entity.AccountStatus;
import com.medicare.identity.entity.AccountTokenType;
import com.medicare.identity.entity.Role;
import com.medicare.identity.util.TokenGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

/**
 * Design doc §20, Workflow M. Creates exactly one SYSTEM_ADMIN account on
 * first startup — solving the chicken-and-egg problem of needing an admin
 * to create the first admin.
 *
 * Deliberately NOT "check if any user exists, then insert" — that has a
 * race window between two instances starting concurrently against an
 * empty database (flagged and fixed in the v1.2 design revision). Instead,
 * the insert itself is the atomic gate, keyed on the unique email
 * constraint already on `users` — ON CONFLICT DO NOTHING means only one
 * instance can ever win, and it's the database enforcing that, not
 * application logic racing against itself.
 *
 * This is a deliberate exception to "everything goes through the ORM/
 * repository layer" — INSERT ... ON CONFLICT ... RETURNING isn't
 * naturally expressed through a standard JPA repository save, and
 * correctness here matters more than consistency of style.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final long ACTIVATION_TOKEN_TTL_HOURS = 48;

    private final JdbcTemplate jdbcTemplate;
    private final TokenGenerator tokenGenerator;

    @Value("${identity.bootstrap.admin-email}")
    private String bootstrapAdminEmail;

    public AdminBootstrapRunner(JdbcTemplate jdbcTemplate, TokenGenerator tokenGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenGenerator = tokenGenerator;
    }

    @Override
    public void run(ApplicationArguments args) {
        String normalizedEmail = bootstrapAdminEmail.trim().toLowerCase(Locale.ROOT);
        UUID userId = UUID.randomUUID();

        // The atomic gate. If a row with this email already exists (this
        // instance lost the race, or bootstrap already happened on a
        // previous run), nothing is returned and nothing else in this
        // method executes.
        var insertedIds = jdbcTemplate.query(
                """
                INSERT INTO users (id, email, first_name, last_name, role, status, failed_login_count, created_at, updated_at)
                VALUES (?, ?, 'System', 'Administrator', ?, ?, 0, now(), now())
                ON CONFLICT (email) DO NOTHING
                RETURNING id
                """,
                (rs, rowNum) -> (UUID) rs.getObject("id"),
                userId, normalizedEmail, Role.SYSTEM_ADMIN.name(), AccountStatus.PENDING_ACTIVATION.name()
        );

        if (insertedIds.isEmpty()) {
            // Bootstrap already happened — expected on every startup after the first.
            return;
        }

        TokenGenerator.GeneratedToken token = tokenGenerator.generate();
        Instant expiresAt = Instant.now().plus(ACTIVATION_TOKEN_TTL_HOURS, ChronoUnit.HOURS);

//        jdbcTemplate.update(
//                """
//                INSERT INTO account_tokens (id, user_id, type, token_hash, expires_at, created_at)
//                VALUES (?, ?, ?, ?, ?, now())
//                """,
//                UUID.randomUUID(), userId, AccountTokenType.ACTIVATION.name(), token.hash(), expiresAt
//        );

        jdbcTemplate.update(
                """
                INSERT INTO account_tokens (id, user_id, type, token_hash, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?, now())
                """,
                UUID.randomUUID(), userId, AccountTokenType.ACTIVATION.name(), token.hash(), Timestamp.from(expiresAt)
        );

        jdbcTemplate.update(
                """
                INSERT INTO audit_events (id, event_type, subject_user_id, occurred_at, metadata)
                VALUES (?, 'SYSTEM_BOOTSTRAP_ADMIN_CREATED', ?, now(), NULL)
                """,
                UUID.randomUUID(), userId
        );

        // Logged, never emailed — Notification Service may not even exist
        // yet (§15), and a bootstrap credential is exactly the kind of
        // thing that should never touch an email pipeline.
        System.out.println("=".repeat(80));
        System.out.println("FIRST-RUN BOOTSTRAP: SYSTEM_ADMIN account created for " + normalizedEmail);
        System.out.println("Activation link (valid " + ACTIVATION_TOKEN_TTL_HOURS + "h):");
        System.out.println("  http://localhost:8081/accounts/activate?token=" + token.rawValue());
        System.out.println("=".repeat(80));
    }
}