package com.tripledger.app.security;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Reads the caller out of the verified token. This is the ONLY place the tenant comes from -
 * never from a header, a path or the request body - so a client can't just ask for another
 * company's data by changing a parameter.
 */
@Component
public class CurrentCaller {

    public Caller get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token)) {
            // the security filter chain makes sure we never get here without a token
            throw new IllegalStateException("no authenticated caller on this request");
        }
        Jwt jwt = token.getToken();
        List<String> roleNames = jwt.getClaimAsStringList(Claims.ROLES);
        Set<Role> roles = EnumSet.noneOf(Role.class);
        roleNames.forEach(name -> roles.add(Role.valueOf(name)));
        return new Caller(jwt.getClaimAsString(Claims.TENANT), jwt.getSubject(), roles,
                jwt.getClaimAsString(Claims.EMPLOYEE));
    }
}
