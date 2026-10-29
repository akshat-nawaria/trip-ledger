package com.tripledger.app.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token settings (tripledger.security.* in application.yml).
 *
 * Tokens are signed with HS256 and a shared secret. That's the simplest thing that works for a
 * single service and a local demo. In a real deployment the tokens would come from the company's
 * login system and we'd verify them with its public keys instead - only the decoder bean changes.
 */
@ConfigurationProperties(prefix = "tripledger.security")
public record JwtSettings(String jwtSecret, String issuer, Duration tokenTtl, boolean devTokens) {

    // HS256 needs a key of at least 256 bits; anything shorter is rejected at startup, not at first login
    private static final int MIN_SECRET_BYTES = 32;

    public JwtSettings {
        Objects.requireNonNull(jwtSecret, "tripledger.security.jwt-secret");
        Objects.requireNonNull(issuer, "tripledger.security.issuer");
        Objects.requireNonNull(tokenTtl, "tripledger.security.token-ttl");
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("tripledger.security.jwt-secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
