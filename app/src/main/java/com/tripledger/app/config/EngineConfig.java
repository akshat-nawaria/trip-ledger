package com.tripledger.app.config;

import com.tripledger.core.config.BillingRules;
import com.tripledger.core.engine.DistanceEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// the core engine as a Spring bean. It holds no state between trips, so one shared instance is fine.
@Configuration
public class EngineConfig {

    // case-study defaults for now. Per-tenant thresholds (e.g. the no-show flag) come with tenant config in S6.
    @Bean
    public BillingRules billingRules() {
        return BillingRules.defaults();
    }

    @Bean
    public DistanceEngine distanceEngine(BillingRules billingRules) {
        return new DistanceEngine(billingRules);
    }
}
