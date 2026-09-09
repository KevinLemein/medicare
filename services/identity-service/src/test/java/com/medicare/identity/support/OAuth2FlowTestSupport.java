package com.medicare.identity.support;

import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives a real authorization_code + PKCE grant against the actual
 * "/oauth2/authorize" and "/oauth2/token" endpoints, the same way the
 * hms-frontend SPA does: hosted-login session -> authorize (no consent
 * screen, since hms-frontend is registered with
 * requireAuthorizationConsent(false) as a first-party client) ->
 * authorization code -> token exchange.
 *
 * Not a mock of the protocol — this exercises the real Spring
 * Authorization Server filter chain end to end, including CSRF and the
 * PKCE code_verifier check.
 */
public final class OAuth2FlowTestSupport {

    private static final Pattern ACCESS_TOKEN_PATTERN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CODE_PARAM_PATTERN = Pattern.compile("[?&]code=([^&]+)");

    private OAuth2FlowTestSupport() {
    }

    /** Logs in, runs the PKCE authorization_code grant, and returns the issued access token. */
    public static String obtainAccessToken(MockMvc mockMvc, String username, String rawPassword,
                                           String clientId, String redirectUri) throws Exception {
        MockHttpSession session = login(mockMvc, username, rawPassword);

        String verifier = randomUrlSafe(32);
        String challenge = s256Challenge(verifier);
        String state = randomUrlSafe(16);

        // Built as a literal query string rather than chained .param(...)
        // calls: for a GET, MockMvc's .param() only populates the servlet
        // parameter map, not request.getQueryString() -- and Spring
        // Authorization Server's authorization-request converter reads the
        // raw query string (it also independently reads the form body, to
        // support the POST/form_post variant), so a .param()-built GET
        // request arrives with no query string and is rejected as missing
        // "response_type". A real browser GET always carries these in the
        // URL, so building the URI directly here matches what actually
        // happens in production.
        //
        // Deliberately NOT percent-encoded: MockMvc's get(String) splits
        // this literal string on '&'/'=' to populate the parameter map
        // without decoding it first, so a pre-encoded value (e.g.
        // "http%3A%2F%2F...") would land in the parameter map still
        // encoded and fail an exact-match check against the registered
        // redirect_uri. None of these values contain '&', '=' or '%', so
        // an unencoded literal query string is unambiguous here.
        Map<String, String> authorizeParams = new LinkedHashMap<>();
        authorizeParams.put("response_type", "code");
        authorizeParams.put("client_id", clientId);
        authorizeParams.put("redirect_uri", redirectUri);
        authorizeParams.put("scope", "openid profile");
        authorizeParams.put("code_challenge", challenge);
        authorizeParams.put("code_challenge_method", "S256");
        authorizeParams.put("state", state);

        MvcResult authorizeResult = mockMvc.perform(get("/oauth2/authorize?" + toQueryString(authorizeParams))
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = authorizeResult.getResponse().getRedirectedUrl();
        if (location == null || !location.startsWith(redirectUri)) {
            throw new IllegalStateException(
                    "Expected a redirect back to " + redirectUri + " with an authorization code, got: " + location
                            + " (consent screen unexpectedly shown, or authorization failed)");
        }
        String code = extractCode(location);

        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", redirectUri)
                        .param("client_id", clientId)
                        .param("code_verifier", verifier))
                .andExpect(status().isOk())
                .andReturn();

        return extractAccessToken(tokenResult.getResponse().getContentAsString());
    }

    private static MockHttpSession login(MockMvc mockMvc, String username, String rawPassword) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("username", username)
                        .param("password", rawPassword)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        if (session == null) {
            throw new IllegalStateException("Login did not establish a session — check credentials/account status");
        }
        return session;
    }

    private static String extractCode(String redirectLocation) {
        Matcher matcher = CODE_PARAM_PATTERN.matcher(redirectLocation);
        if (!matcher.find()) {
            throw new IllegalStateException("No authorization code in redirect: " + redirectLocation);
        }
        return java.net.URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
    }

    private static String extractAccessToken(String tokenResponseBody) {
        Matcher matcher = ACCESS_TOKEN_PATTERN.matcher(tokenResponseBody);
        if (!matcher.find()) {
            throw new IllegalStateException("No access_token in token response: " + tokenResponseBody);
        }
        return matcher.group(1);
    }

    private static String toQueryString(Map<String, String> params) {
        StringBuilder builder = new StringBuilder();
        params.forEach((key, value) -> {
            if (!builder.isEmpty()) {
                builder.append('&');
            }
            builder.append(key).append('=').append(value);
        });
        return builder.toString();
    }

    private static String randomUrlSafe(int numBytes) {
        byte[] bytes = new byte[numBytes];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String s256Challenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
