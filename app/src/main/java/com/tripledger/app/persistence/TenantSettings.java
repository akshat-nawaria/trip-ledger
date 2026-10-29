package com.tripledger.app.persistence;

import com.tripledger.core.config.BillingRules;

/**
 * The billing settings a client company can change. Everything else in BillingRules stays at the
 * case-study value for everyone (keeps the number of things that can differ between tenants small).
 *
 * version 0 = never changed, i.e. the case-study defaults.
 */
public record TenantSettings(
        int version,
        boolean noShowAnchorEnabled,
        int maxAccuracyMetres,
        double maxSpeedKmph,
        int officeRadiusMetres,
        int maxRouteKm) {

    public static TenantSettings caseStudyDefaults() {
        BillingRules d = BillingRules.defaults();
        return new TenantSettings(0, d.noShowAnchorEnabled(), d.maxAccuracyMetres(), d.maxSpeedKmph(),
                d.officeRadiusMetres(), d.maxRouteKm());
    }

    // BillingRules checks every value (positive, parked < max speed, ...) and throws if one is off,
    // so building the rules is also how we validate a settings change
    public BillingRules toRules() {
        return BillingRules.defaults().toBuilder()
                .noShowAnchorEnabled(noShowAnchorEnabled)
                .maxAccuracyMetres(maxAccuracyMetres)
                .maxSpeedKmph(maxSpeedKmph)
                .officeRadiusMetres(officeRadiusMetres)
                .maxRouteKm(maxRouteKm)
                .build();
    }

    public TenantSettings withVersion(int newVersion) {
        return new TenantSettings(newVersion, noShowAnchorEnabled, maxAccuracyMetres, maxSpeedKmph,
                officeRadiusMetres, maxRouteKm);
    }
}
