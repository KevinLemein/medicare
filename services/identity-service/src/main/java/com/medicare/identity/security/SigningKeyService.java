package com.medicare.identity.security;

import com.nimbusds.jose.jwk.RSAKey;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;

/**
 * Loads this service's RSA JWT-signing key from the database, generating
 * and persisting one on first startup.
 *
 * Previously a fresh RSA keypair was generated in memory on every process
 * start (see git history of AuthorizationServerConfig.jwkSource), which
 * meant every access/refresh token stopped validating the moment the
 * service restarted. This persists the key the same race-safe way
 * AdminBootstrapRunner persists the first admin: generate a candidate, try
 * to INSERT it with ON CONFLICT DO NOTHING keyed on a fixed row id, then
 * SELECT whichever row actually won — safe if two instances start
 * concurrently against an empty database.
 */
@Component
public class SigningKeyService {

    private static final String SIGNING_KEY_ROW_ID = "primary";

    private final JdbcTemplate jdbcTemplate;

    public SigningKeyService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RSAKey loadOrCreateSigningKey() {
        KeyPair candidate = generateRsaKeyPair();
        String candidateKeyId = UUID.randomUUID().toString();

        jdbcTemplate.update(
                """
                INSERT INTO signing_keys (id, key_id, private_key, public_key, created_at)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (id) DO NOTHING
                """,
                SIGNING_KEY_ROW_ID, candidateKeyId,
                candidate.getPrivate().getEncoded(), candidate.getPublic().getEncoded()
        );

        return jdbcTemplate.queryForObject(
                "SELECT key_id, private_key, public_key FROM signing_keys WHERE id = ?",
                (rs, rowNum) -> toRsaKey(
                        rs.getString("key_id"),
                        rs.getBytes("private_key"),
                        rs.getBytes("public_key")
                ),
                SIGNING_KEY_ROW_ID
        );
    }

    private RSAKey toRsaKey(String keyId, byte[] privateKeyBytes, byte[] publicKeyBytes) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPrivateKey privateKey =
                    (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
            RSAPublicKey publicKey =
                    (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));

            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(keyId)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to reconstruct persisted RSA signing key", e);
        }
    }

    private KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA key pair for JWT signing", e);
        }
    }
}
