package com.tripledger.app.security;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Extra checks on top of "signature ok, not expired": a token must say which tenant it belongs to,
 * and carry at least one role we know. A token missing either is useless to us, so it's rejected
 * right at the door (401) instead of failing somewhere deep inside a query.
 */
public class TenantClaimValidator implements OAuth2TokenValidator<Jwt> {

    private static final Pattern TENANT = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String tenant = jwt.getClaimAsString(Claims.TENANT);
        if (tenant == null || !TENANT.matcher(tenant).matches()) {
            return failure("token has no valid " + Claims.TENANT + " claim");
        }
        List<String> roles = jwt.getClaimAsStringList(Claims.ROLES);
        if (roles == null || roles.isEmpty() || !roles.stream().allMatch(Role::isKnown)) {
            return failure("token has no valid " + Claims.ROLES + " claim");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null));
    }
}
