package com.medicare.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration

public class PasswordEncoderConfig {

    // Argon2id. This one bean is used for both user passwords
    // (PasswordService) and OAuth2 client secret verification
    // (AuthorizationServerConfig) — one hashing scheme in play, not two.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
