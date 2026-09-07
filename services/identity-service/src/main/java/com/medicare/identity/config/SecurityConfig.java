package com.medicare.identity.config;

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

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            String role = jwt.getClaimAsString("role");

            if (role == null || role.isBlank()) {
                return List.of();
            }

            return List.of(
                    new SimpleGrantedAuthority("ROLE_" + role)
            );
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
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {

        http
                .authorizeHttpRequests(authorize -> authorize

                        // Public account-management endpoints
                        .requestMatchers(
                                "/accounts/activate",
                                "/accounts/password-reset/**"
                        ).permitAll()

                        // Login/error pages
                        .requestMatchers(
                                "/login",
                                "/error"
                        ).permitAll()

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )

                // Hosted login page
                .formLogin(form -> form.permitAll())

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