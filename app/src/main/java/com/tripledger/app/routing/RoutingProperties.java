package com.tripledger.app.routing;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every routing knob in one place: tripledger.routing.* in application.yml.
 * All of it can change per environment without touching code.
 *
 * Fail fast on silly values here, at startup. Much better than finding out from a weird bill.
 */
@ConfigurationProperties(prefix = "tripledger.routing")
public record RoutingProperties(
        String provider,
        long cellMicroDegrees,
        CacheSettings cache,
        BreakerSettings breaker,
        MockSettings mock) {

    public RoutingProperties {
        Objects.requireNonNull(provider, "tripledger.routing.provider");
        Objects.requireNonNull(cache, "tripledger.routing.cache");
        Objects.requireNonNull(breaker, "tripledger.routing.breaker");
        Objects.requireNonNull(mock, "tripledger.routing.mock");
        if (cellMicroDegrees <= 0) {
            throw new IllegalArgumentException("tripledger.routing.cell-micro-degrees must be positive");
        }
    }

    public record CacheSettings(long maxEntries, Duration timeToLive) {

        public CacheSettings {
            Objects.requireNonNull(timeToLive, "tripledger.routing.cache.time-to-live");
        }
    }

    public record BreakerSettings(
            int windowSize,
            int minimumCalls,
            float failureRatePercent,
            Duration waitInOpen,
            int halfOpenCalls) {

        public BreakerSettings {
            Objects.requireNonNull(waitInOpen, "tripledger.routing.breaker.wait-in-open");
        }
    }

    public record MockSettings(double roadFactor) {
    }
}
