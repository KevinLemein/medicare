package com.medicare.identity.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * §11/§17: seeds the two registered OAuth2 clients on startup, idempotently
 * (checked by client_id, not re-inserted if already present). Deliberately
 * NOT a Flyway migration — a client secret has no business in version
 * control, and hms-frontend's real redirect URI is environment-specific.
 * Mirrors the same "startup component, not a migration" shape as the
 * admin-bootstrap logic (Workflow M).
 */
@Component
public class ClientRegistrationInitializer implements ApplicationRunner {

    private final RegisteredClientRepository registeredClientRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${identity.oauth2.frontend-redirect-uri}")
    private String frontendRedirectUri;

    @Value("${identity.oauth2.patient-service-client-secret}")
    private String patientServiceClientSecret;

    public ClientRegistrationInitializer(RegisteredClientRepository registeredClientRepository,
                                         PasswordEncoder passwordEncoder) {
        this.registeredClientRepository = registeredClientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        // §12: 5-minute access token cap lives here, per-client.
        TokenSettings tokenSettings = TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(5))
                .build();

        if (registeredClientRepository.findByClientId("hms-frontend") == null) {
            RegisteredClient hmsFrontend = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("hms-frontend")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE) // §9/§11: public client, no secret
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri(frontendRedirectUri)
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .clientSettings(ClientSettings.builder()
                            .requireProofKey(true)              // PKCE mandatory (§9)
                            .requireAuthorizationConsent(false)  // first-party client — no consent screen
                            .build())
                    .tokenSettings(tokenSettings)
                    .build();
            registeredClientRepository.save(hmsFrontend);
        }

        if (registeredClientRepository.findByClientId("patient-service") == null) {
            RegisteredClient patientService = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("patient-service")
                    .clientSecret(passwordEncoder.encode(patientServiceClientSecret)) // same Argon2 bean as user passwords
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .scope("identity:provision-patient") // §11/§17 — scope, not role
                    .tokenSettings(tokenSettings)
                    .build();
            registeredClientRepository.save(patientService);
        }
    }
}
