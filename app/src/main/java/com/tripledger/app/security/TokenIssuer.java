package com.tripledger.app.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Makes signed tokens. Used by the dev token endpoint (local demo) and by the tests.
 * In production nobody calls this - tokens come from the company's own login system.
 */
@Component
public class TokenIssuer {

    private final JwtEncoder encoder;
    private final JwtSettings settings;

    public TokenIssuer(JwtEncoder encoder, JwtSettings settings) {
        this.encoder = encoder;
        this.settings = settings;
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public IssuedToken issue(String tenant, String subject, Set<Role> roles, String employeeRef) {
        return issue(tenant, subject, roles, employeeRef, Instant.now(), settings.tokenTtl());
    }

    // issuedAt can be in the past on purpose - tests use that to make a token that has already expired
    // (a token can't simply expire before it was issued; Spring refuses to build one like that)
    public IssuedToken issue(String tenant, String subject, Set<Role> roles, String employeeRef, Instant issuedAt,
            Duration ttl) {
        Instant expiresAt = issuedAt.plus(ttl);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(settings.issuer())
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(Claims.TENANT, tenant)
                .claim(Claims.ROLES, roles.stream().map(Enum::name).sorted().toList());
        if (employeeRef != null) {
            claims.claim(Claims.EMPLOYEE, employeeRef);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
