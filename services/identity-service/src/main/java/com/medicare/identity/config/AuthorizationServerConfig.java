package com.medicare.identity.config;

import com.medicare.identity.repository.UserRepository;
import com.medicare.identity.security.SessionCsrfRequirementMatcher;
import com.medicare.identity.security.SigningKeyService;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

@Configuration
public class AuthorizationServerConfig {

    @Value("${identity.oauth2.issuer-uri}")
    private String issuerUri;

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http, SessionCsrfRequirementMatcher sessionCsrfRequirementMatcher) throws Exception {

        http
                .oauth2AuthorizationServer(authorizationServer -> {
                    http.securityMatcher(authorizationServer.getEndpointsMatcher());

                    authorizationServer
                            .oidc(oidc -> {});
                })
                .exceptionHandling(exceptions ->
                        exceptions.defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                        )
                )
                // Same policy as SecurityConfig's chain (see
                // SessionCsrfRequirementMatcher): required only for
                // state-changing requests that already carry a session. That
                // protects a browser's POST to /oauth2/authorize (consent
                // approval) from being forged, while leaving the
                // non-browser, non-session /oauth2/token exchange (PKCE
                // public client or client_credentials) unaffected — it was
                // previously not exempted at all, which would have made a
                // real token exchange fail CSRF validation.
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(sessionCsrfRequirementMatcher));

        return http.build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
                                                           RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(SigningKeyService signingKeyService) {
        RSAKey rsaKey = signingKeyService.loadOrCreateSigningKey();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        // Reuses the in-memory jwkSource bean directly — no HTTP round trip
        // to this same application's own /oauth2/jwks endpoint.
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(issuerUri)
                .build();
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer(UserRepository userRepository) {
        return context -> {
            String principalName = context.getPrincipal().getName();

            if (context.getTokenType().getValue().equals("access_token")) {
                userRepository.findByEmail(principalName).ifPresent(user ->
                        context.getClaims().claim("role", user.getRole().name())
                );
            }

            if (context.getTokenType().getValue().equals("id_token")) {
                userRepository.findByEmail(principalName).ifPresent(user -> {
                    context.getClaims().claim("given_name", user.getFirstName());
                    context.getClaims().claim("family_name", user.getLastName());
                });
            }
        };
    }
}