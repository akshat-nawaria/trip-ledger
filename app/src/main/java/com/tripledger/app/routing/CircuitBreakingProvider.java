package com.tripledger.app.routing;

import com.tripledger.core.geo.Distance;
import com.tripledger.core.model.GeoPoint;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

/**
 * Wraps a provider in a circuit breaker (Resilience4j).
 *
 * Why bother: when the map service is down, every gap would sit waiting for a timeout and then
 * fail anyway. Once enough calls fail, the breaker "opens" and we stop calling for a while.
 * Requests fail instantly, and the engine falls back to straight line x 1.4 (DEGRADED).
 * After the wait it lets a few trial calls through. If they work it closes again, if not it
 * stays open for another round.
 *
 * Settings come from application.yml (tripledger.routing.breaker). Defaults: look at the last
 * 10 calls, need at least 5, open at 50% failures, stay open 30 s, then allow 3 trial calls.
 */
public final class CircuitBreakingProvider implements RoutingProvider {

    private final RoutingProvider delegate;
    private final CircuitBreaker breaker;

    public CircuitBreakingProvider(RoutingProvider delegate, CircuitBreaker breaker) {
        this.delegate = delegate;
        this.breaker = breaker;
    }

    public static CircuitBreaker newBreaker(String name, RoutingProperties.BreakerSettings settings) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.windowSize())
                .minimumNumberOfCalls(settings.minimumCalls())
                .failureRateThreshold(settings.failureRatePercent())
                .waitDurationInOpenState(settings.waitInOpen())
                .permittedNumberOfCallsInHalfOpenState(settings.halfOpenCalls())
                .build();
        return CircuitBreaker.of(name, config);
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public Distance drivingDistance(GeoPoint from, GeoPoint to) {
        try {
            return breaker.executeSupplier(() -> delegate.drivingDistance(from, to));
        } catch (CallNotPermittedException e) {
            throw new RoutingCircuitOpenException("routing circuit is open, not calling " + delegate.name());
        }
    }

    public CircuitBreaker.State state() {
        return breaker.getState();
    }
}
