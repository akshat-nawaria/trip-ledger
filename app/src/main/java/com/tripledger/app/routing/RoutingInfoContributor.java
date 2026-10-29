package com.tripledger.app.routing;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.Map;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

// shows the routing setup on GET /actuator/info: which provider, is the breaker open, how big
// the cache is. Handy in a demo - flip the provider off and watch the breaker go OPEN.
@Component
public class RoutingInfoContributor implements InfoContributor {

    private final RoutingProvider provider;
    private final CircuitBreaker breaker;
    private final RouteCache cache;

    public RoutingInfoContributor(RoutingProvider routingProvider, CircuitBreaker routingCircuitBreaker,
            RouteCache routeCache) {
        this.provider = routingProvider;
        this.breaker = routingCircuitBreaker;
        this.cache = routeCache;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("routing", Map.of(
                "provider", provider.name(),
                "breaker", breaker.getState().name(),
                "cachedRoutes", cache.size()));
    }
}
