package com.tripledger.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class BillingRulesTest {

    @Test
    void defaultsMatchTheCaseStudy() {
        BillingRules rules = BillingRules.defaults();

        assertThat(rules.maxSpeedKmph()).isEqualTo(120.0);
        assertThat(rules.shortGap()).isEqualTo(Duration.ofSeconds(70));
        assertThat(rules.parkedSpeedKmph()).isEqualTo(5.0);
        assertThat(rules.officeRadiusMetres()).isEqualTo(400);
        assertThat(rules.maxRouteKm()).isEqualTo(100);
        assertThat(rules.degradedRouteFactor()).isEqualTo(1.4);
        assertThat(rules.maxAccuracyMetres()).isEqualTo(50);
        assertThat(rules.noShowAnchorEnabled()).isFalse();
    }

    @Test
    void builderOverridesOneValueAndKeepsTheRest() {
        BillingRules rules = BillingRules.builder().maxSpeedKmph(100).build();

        assertThat(rules.maxSpeedKmph()).isEqualTo(100.0);
        assertThat(rules.officeRadiusMetres()).isEqualTo(400);
    }

    @Test
    void toBuilderCopiesEveryValue() {
        BillingRules original = BillingRules.builder().officeRadiusMetres(250).noShowAnchorEnabled(true).build();

        assertThat(original.toBuilder().build()).isEqualTo(original);
    }

    @Test
    void rejectsNonPositiveSpeedLimit() {
        assertThatThrownBy(() -> BillingRules.builder().maxSpeedKmph(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDegradedFactorBelowOne() {
        assertThatThrownBy(() -> BillingRules.builder().degradedRouteFactor(0.9).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsParkedSpeedAtOrAboveTheSpeedLimit() {
        assertThatThrownBy(() -> BillingRules.builder().parkedSpeedKmph(150).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsReanchorRunBelowTwo() {
        assertThatThrownBy(() -> BillingRules.builder().teleportReanchorRun(1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
