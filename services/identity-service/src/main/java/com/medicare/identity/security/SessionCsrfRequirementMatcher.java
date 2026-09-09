package com.medicare.identity.security;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

/**
 * Decides which requests must carry a CSRF token.
 *
 * This service has two ways to authenticate on the same origin: a browser
 * session/cookie (the hosted /login page, and consent on /oauth2/authorize)
 * and a stateless Bearer JWT / OAuth2 client credential (the SPA's API
 * calls, patient-service, admin tooling). Exempting whole path prefixes
 * from CSRF checking (the previous approach, on the assumption that paths
 * like "/admin/**" are only ever called with a Bearer token) is unsound:
 * both filter chains share the same session cookie jar, so if a
 * SYSTEM_ADMIN's browser ever holds an authenticated session on this
 * origin, that cookie is attached automatically to *any* same-site
 * request — including a forged one from an attacker's page — and a
 * path-based exemption would let it through unchecked.
 *
 * Instead, CSRF is required exactly when both are true:
 *   - the HTTP method is state-changing (not GET/HEAD/TRACE/OPTIONS)
 *   - the request already carries a valid session, i.e. it could be
 *     riding on an authenticated browser session
 *
 * A stateless Bearer-JWT or PKCE public-client request never has a
 * session, so it's never asked for a CSRF token it has no way to obtain.
 * Any request that *does* arrive with a session — the one case a forged
 * cross-site request could actually exploit — is protected, regardless of
 * which URL it targets. Used by both SecurityConfig's and
 * AuthorizationServerConfig's filter chains so the policy is uniform.
 */
@Component
public class SessionCsrfRequirementMatcher implements RequestMatcher {

    @Override
    public boolean matches(HttpServletRequest request) {
        String method = request.getMethod();
        boolean unsafeMethod = !("GET".equals(method) || "HEAD".equals(method)
                || "TRACE".equals(method) || "OPTIONS".equals(method));
        return unsafeMethod && request.getSession(false) != null;
    }
}
