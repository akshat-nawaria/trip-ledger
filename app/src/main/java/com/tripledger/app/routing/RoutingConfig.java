package com.tripledger.app.routing;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// wires the routing chain together: provider -> breaker -> cache -> gateway
@Configuration
@EnableConfigurationProperties(RoutingProperties.class)
public class RoutingConfig {

    // only the mock exists right now. A real provider (OSRM, Google...) slots in right here,
    // picked by tripledger.routing.provider, and nothing else in the app has to change.
    @Bean
    public RoutingProvider routingProvider(RoutingProperties props) {
        if (!MockRoutingProvider.NAME.equals(props.provider())) {
            throw new IllegalStateException("unknown routing provider '" + props.provider()
                    + "' (only '" + MockRoutingProvider.NAME + "' is available so far)");
        }
        return new MockRoutingProvider(props.mock().roadFactor());
    }

    @Bean
    public CircuitBreaker routingCircuitBreaker(RoutingProperties props) {
        return CircuitBreakingProvider.newBreaker("routing", props.breaker());
    }

    @Bean
    public RouteCache routeCache(RoutingProperties props) {
        return new RouteCache(props.cache().maxEntries(), props.cache().timeToLive());
    }

    // the breaker-wrapped provider is deliberately NOT its own bean, otherwise there'd be two
    // RoutingProvider beans and Spring wouldn't know which one to inject where
    @Bean
    public RoutingGateway routingGateway(RoutingProvider routingProvider, CircuitBreaker routingCircuitBreaker,
            RouteCache routeCache, RoutingProperties props) {
        RoutingProvider protectedProvider = new CircuitBreakingProvider(routingProvider, routingCircuitBreaker);
        return new RoutingGateway(protectedProvider, routeCache, props.cellMicroDegrees());
    }
}
