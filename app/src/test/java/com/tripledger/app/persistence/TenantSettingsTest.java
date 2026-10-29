package com.tripledger.app.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tripledger.core.config.BillingRules;
import org.junit.jupiter.api.Test;

class TenantSettingsTest {

    @Test
    void defaultsAreExactlyTheCaseStudyRules() {
        TenantSettings defaults = TenantSettings.caseStudyDefaults();

        assertThat(defaults.version()).isEqualTo(0);
        assertThat(defaults.toRules()).isEqualTo(BillingRules.defaults());
    }

    @Test
    void changedValuesEndUpInTheRulesAndNothingElseMoves() {
        TenantSettings custom = new TenantSettings(3, true, 30, 100.0, 250, 80);

        BillingRules rules = custom.toRules();

        assertThat(rules.noShowAnchorEnabled()).isTrue();
        assertThat(rules.maxAccuracyMetres()).isEqualTo(30);
        assertThat(rules.maxSpeedKmph()).isEqualTo(100.0);
        assertThat(rules.officeRadiusMetres()).isEqualTo(250);
        assertThat(rules.maxRouteKm()).isEqualTo(80);
        // untouched by tenant settings
        assertThat(rules.shortGap()).isEqualTo(BillingRules.defaults().shortGap());
        assertThat(rules.degradedRouteFactor()).isEqualTo(BillingRules.defaults().degradedRouteFactor());
    }

    @Test
    void nonsenseValuesAreRefusedByTheRulesThemselves() {
        assertThatThrownBy(() -> new TenantSettings(1, false, 0, 120.0, 400, 100).toRules())
                .isInstanceOf(IllegalArgumentException.class);
        // a "max speed" below the parked speed (5 km/h) can't be right either
        assertThatThrownBy(() -> new TenantSettings(1, false, 50, 4.0, 400, 100).toRules())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withVersionOnlyChangesTheVersion() {
        TenantSettings s = new TenantSettings(0, true, 30, 100.0, 250, 80);

        assertThat(s.withVersion(7)).isEqualTo(new TenantSettings(7, true, 30, 100.0, 250, 80));
    }
}
