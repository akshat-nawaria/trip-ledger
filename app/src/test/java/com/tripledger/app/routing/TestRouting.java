package com.tripledger.app.routing;

import com.tripledger.core.model.GeoPoint;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;

// shared bits for the routing tests: same breaker settings as application.yml, and a helper
// to build the full chain (provider -> breaker -> cache -> gateway) without Spring
final class TestRouting {

    static final long CELL = 200; // same 0.0002 deg grid as application.yml

    static final RoutingProperties.BreakerSettings BREAKER =
            new RoutingProperties.BreakerSettings(10, 5, 50f, Duration.ofSeconds(30), 3);

    private TestRouting() {
    }

    static CircuitBreaker newBreaker() {
        return CircuitBreakingProvider.newBreaker("test-routing", BREAKER);
    }

    static RouteCache newCache() {
        return new RouteCache(10_000, Duration.ofHours(1));
    }

    static RoutingGateway gateway(RoutingProvider provider, CircuitBreaker breaker) {
        return new RoutingGateway(new CircuitBreakingProvider(provider, breaker), newCache(), CELL);
    }

    // a point roughly i km north of the office, always well inside its own grid cell
    // (50 micro-degrees into a 200-wide cell), so tests never land on a cell edge by accident
    static GeoPoint place(int i) {
        return new GeoPoint(12_970_050L + i * 9_000L, 77_750_050L);
    }
}
