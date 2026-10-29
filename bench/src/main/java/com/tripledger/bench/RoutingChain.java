package com.tripledger.bench;

import com.tripledger.app.routing.CircuitBreakingProvider;
import com.tripledger.app.routing.MockRoutingProvider;
import com.tripledger.app.routing.RouteCache;
import com.tripledger.app.routing.RoutingGateway;
import com.tripledger.app.routing.RoutingProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;

/**
 * The app's real routing chain (snap -> cache -> circuit breaker -> mock provider), built by hand
 * exactly like RoutingConfig does it, with the application.yml values. No Spring, no database:
 * the benchmark measures the engine and the routing layer, the API is k6's job.
 *
 * One difference: the breaker's wait-in-open. The benchmark squeezes an evening's trips into
 * seconds, so a 30 s wait would cover thousands of trips. It's scaled down to keep the story the
 * same as in real time: fail, open, wait, test, close.
 */
record RoutingChain(MockRoutingProvider provider, CircuitBreaker breaker, RouteCache cache, RoutingGateway gateway) {

    static RoutingChain fresh(Duration breakerWait) {
        MockRoutingProvider provider = new MockRoutingProvider(1.3);
        CircuitBreaker breaker = CircuitBreakingProvider.newBreaker("bench-routing",
                new RoutingProperties.BreakerSettings(10, 5, 50f, breakerWait, 3));
        RouteCache cache = new RouteCache(100_000, Duration.ofDays(7));
        RoutingGateway gateway = new RoutingGateway(new CircuitBreakingProvider(provider, breaker), cache, 200);
        return new RoutingChain(provider, breaker, cache, gateway);
    }

    int trialCalls() {
        return breaker.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState();
    }
}
