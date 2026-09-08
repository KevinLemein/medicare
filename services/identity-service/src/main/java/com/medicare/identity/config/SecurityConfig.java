package com.medicare.identity.config;

import com.medicare.identity.security.LoginAuditAuthenticationFailureHandler;
import com.medicare.identity.security.LoginAuditAuthenticationSuccessHandler;
import com.medicare.identity.service.IdentityUserDetailsService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * The "everything that isn't the OAuth2 protocol itself" filter chain:
 * the hosted login page, and the public account-management endpoints
 * (activation, password reset) that don't require authentication.
 *
 * Two filter chains exist in this service:
 *   1. AuthorizationServerConfig - OAuth2/OIDC protocol endpoints
 *   2. This class - login and normal application endpoints
 *
 * The Authorization Server chain is @Order(1), so it gets evaluated first.
 */
@Configuration
public class SecurityConfig {

    /**
     * Converts the custom "role" claim in our JWT access tokens into
     * Spring Security authorities.
     *
     * Example:
     *
     *     role = "DOCTOR"
     *
     * becomes:
     *
     *     ROLE_DOCTOR
     *
     * This allows:
     *
     *     hasRole("DOCTOR")
     *
     * to work in resource-server protected endpoints.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        // Default converter handles the standard "scope"/"scp" claim,
        // producing SCOPE_xxx authorities — this is what lets
        // hasAuthority("SCOPE_identity:provision-patient") work for
        // client_credentials tokens (patient-service, etc).
        org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
                defaultScopesConverter =
                new org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<org.springframework.security.core.GrantedAuthority> authorities = new java.util.ArrayList<>();

            // Human users: our custom "role" claim -> ROLE_xxx
            String role = jwt.getClaimAsString("role");
            if (role != null && !role.isBlank()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }

            // Machine clients: standard scope claim -> SCOPE_xxx
            authorities.addAll(defaultScopesConverter.convert(jwt));

            return authorities;
        });

        return converter;
    }

    /**
     * Default security chain.
     *
     * This handles:
     * - hosted login page
     * - account activation
     * - password reset
     * - authenticated non-OAuth2 requests
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            LoginAuditAuthenticationSuccessHandler loginAuditAuthenticationSuccessHandler,
            LoginAuditAuthenticationFailureHandler loginAuditAuthenticationFailureHandler
    ) throws Exception {

        http
                .authorizeHttpRequests(authorize -> authorize

                        // Public account-management endpoints
                        .requestMatchers(
                                "/accounts/activate",
                                "/accounts/password-reset/**"
                        ).permitAll()
                        .requestMatchers("/internal/patients/provision")
                        .hasAuthority("SCOPE_identity:provision-patient")

                        // Login/error pages
                        .requestMatchers(
                                "/login",
                                "/error"
                        ).permitAll()

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )

                // Hosted login page — success/failure handlers wire the
                // lockout-tracking and audit side effects that plain
                // form.permitAll() never triggered on its own.
                .formLogin(form -> form
                        .permitAll()
                        .successHandler(loginAuditAuthenticationSuccessHandler)
                        .failureHandler(loginAuditAuthenticationFailureHandler)
                )

                // CSRF protection is session/cookie-based and only makes
                // sense for the browser-rendered hosted login form (which
                // still benefits — it guards against login-CSRF). The
                // account-management endpoints below are a JSON API called
                // by non-browser or Bearer-token clients (the SPA via
                // fetch, patient-service via client_credentials) that never
                // carry a session-bound CSRF token, so the check is
                // exempted for those paths.
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/accounts/**",
                        "/internal/**"
                ))

                // Allow this service to validate JWT access tokens
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )
                        )
                );

        return http.build();
    }

    /**
     * Authentication provider used by the hosted login page.
     *
     * Credentials are loaded through IdentityUserDetailsService
     * and passwords are checked using the configured PasswordEncoder.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            IdentityUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }
}
