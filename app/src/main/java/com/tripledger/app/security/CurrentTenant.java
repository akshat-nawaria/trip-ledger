package com.tripledger.app.security;

import org.springframework.stereotype.Component;

/**
 * Which client company (tenant) the current request belongs to.
 *
 * Every query already filters by tenant, so the data is split properly from day one.
 * For now there's just one tenant. S6 swaps this for the tenant inside the caller's JWT,
 * and nothing else in the app has to change.
 */
@Component
public class CurrentTenant {

    public static final String DEFAULT_TENANT = "default";

    public String id() {
        return DEFAULT_TENANT;
    }
}
