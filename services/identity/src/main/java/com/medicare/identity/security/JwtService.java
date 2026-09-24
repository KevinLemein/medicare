package com.medicare.identity.security;

import com.medicare.identity.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {

    private final PrivateKey privateKey;
    private final long expiryMinutes;

    public JwtService(
            @Value("${app.jwt.private-key-path}") String privateKeyPath,
            @Value("${app.jwt.expiry-minutes:60}") long expiryMinutes) throws Exception {
        String pem = Files.readString(Path.of(privateKeyPath));
        String base64Body = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(base64Body);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        this.privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        this.expiryMinutes = expiryMinutes;
    }

    public String issueToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiryMinutes, ChronoUnit.MINUTES)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public Claims parseClaims(String token) {
        throw new UnsupportedOperationException("Identity Service does not verify tokens");
    }
}