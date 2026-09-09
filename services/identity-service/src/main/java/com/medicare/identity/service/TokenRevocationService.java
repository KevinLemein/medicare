package com.medicare.identity.service;

import com.medicare.identity.entity.AuditEventType;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Revokes every OAuth2 authorization (issued access/refresh tokens,
 * in-flight authorization codes) held by a given principal, across all
 * registered clients.
 *
 * Needed anywhere an account's credentials or standing access change in a
 * way that should invalidate tokens already handed out under the old
 * state: password reset/change (the old credential shouldn't keep a
 * session alive), a role change (a still-valid token would carry the
 * stale "role" claim until it naturally expired), and
 * suspension/deactivation (the whole point of suspending an account is
 * that it can no longer act). Reactivating or unlocking an account is
 * deliberately NOT wired to this — those restore access, they don't need
 * to revoke it.
 *
 * OAuth2AuthorizationService (JdbcOAuth2AuthorizationService) has no
 * "find all authorizations for this principal" method — only
 * findById/findByToken — so the id lookup goes straight at
 * oauth2_authorization via JdbcTemplate, then each row is loaded and
 * removed through the real service so its own bookkeeping stays
 * consistent.
 */
@Service
public class TokenRevocationService {

    private final OAuth2AuthorizationService authorizationService;
    private final JdbcTemplate jdbcTemplate;
    private final AuditEventService auditEventService;

    public TokenRevocationService(OAuth2AuthorizationService authorizationService,
                                  JdbcTemplate jdbcTemplate,
                                  AuditEventService auditEventService) {
        this.authorizationService = authorizationService;
        this.jdbcTemplate = jdbcTemplate;
        this.auditEventService = auditEventService;
    }

    @Transactional
    public void revokeAllForPrincipal(String principalName, UUID subjectUserId) {
        List<String> authorizationIds = jdbcTemplate.queryForList(
                "SELECT id FROM oauth2_authorization WHERE principal_name = ?",
                String.class, principalName);

        for (String id : authorizationIds) {
            OAuth2Authorization authorization = authorizationService.findById(id);
            if (authorization != null) {
                authorizationService.remove(authorization);
            }
        }

        if (!authorizationIds.isEmpty()) {
            auditEventService.recordSystem(AuditEventType.TOKEN_REVOKED, subjectUserId,
                    Map.of("revokedCount", authorizationIds.size()));
        }
    }
}
