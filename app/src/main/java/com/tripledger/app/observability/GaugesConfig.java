package com.tripledger.app.observability;

import com.tripledger.app.persistence.JobRepository;
import com.tripledger.app.routing.MockRoutingProvider;
import com.tripledger.app.routing.RouteCache;
import com.tripledger.app.routing.RoutingProvider;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gauges = "what is it right now", read fresh every time Prometheus scrapes (every 5 s in compose).
 *
 *   tripledger.routing.breaker.state        0 closed, 1 half-open, 2 open (anything else: 3)
 *   tripledger.routing.cache.size / .hit.ratio
 *   tripledger.routing.provider.down        1 while the mock provider is switched off (demo only)
 *   tripledger.jobs.queue{status}           pending / running / failed_24h
 *   tripledger.jobs.oldest.pending.age      seconds the oldest waiting job has waited = compute lag
 *
 * The job gauges run a tiny COUNT query per scrape. If the database is down the gauge just
 * reads NaN for that scrape; it never breaks the app.
 */
@Configuration
public class GaugesConfig {

    @Bean
    public MeterBinder routingGauges(CircuitBreaker routingCircuitBreaker, RouteCache routeCache,
            RoutingProvider routingProvider) {
        return registry -> {
            Gauge.builder("tripledger.routing.breaker.state", routingCircuitBreaker, GaugesConfig::breakerState)
                    .description("Routing circuit breaker: 0 closed, 1 half-open, 2 open")
                    .register(registry);
            Gauge.builder("tripledger.routing.cache.size", routeCache, RouteCache::size)
                    .description("Road distances held in the route cache")
                    .register(registry);
            Gauge.builder("tripledger.routing.cache.hit.ratio", routeCache, RouteCache::hitRate)
                    .description("Share of route lookups answered by the cache")
                    .register(registry);
            if (routingProvider instanceof MockRoutingProvider mock) {
                Gauge.builder("tripledger.routing.provider.down", mock, m -> m.isDown() ? 1 : 0)
                        .description("1 while the mock routing provider is switched off")
                        .register(registry);
            }
        };
    }

    @Bean
    public MeterBinder jobGauges(JobRepository jobs) {
        return registry -> {
            Gauge.builder("tripledger.jobs.queue", jobs, j -> j.countByStatus("PENDING"))
                    .tag("status", "pending").description("Compute jobs waiting").register(registry);
            Gauge.builder("tripledger.jobs.queue", jobs, j -> j.countByStatus("RUNNING"))
                    .tag("status", "running").description("Compute jobs being worked on").register(registry);
            Gauge.builder("tripledger.jobs.queue", jobs, JobRepository::countFailedLastDay)
                    .tag("status", "failed_24h").description("Compute jobs that gave up in the last 24 h").register(registry);
            Gauge.builder("tripledger.jobs.oldest.pending.age", jobs, JobRepository::oldestPendingAgeSeconds)
                    .baseUnit("seconds").description("How long the oldest waiting job has waited (compute lag)")
                    .register(registry);
        };
    }

    private static double breakerState(CircuitBreaker breaker) {
        return switch (breaker.getState()) {
            case CLOSED -> 0;
            case HALF_OPEN -> 1;
            case OPEN -> 2;
            default -> 3;
        };
    }
}
