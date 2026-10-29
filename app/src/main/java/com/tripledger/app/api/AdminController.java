package com.tripledger.app.api;

import com.tripledger.app.persistence.JobRepository;
import com.tripledger.app.routing.MockRoutingProvider;
import com.tripledger.app.routing.RouteCache;
import com.tripledger.app.routing.RoutingProvider;
import com.tripledger.app.service.InvalidRequestException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A small operations view for admins and the demo page: is routing healthy, how full is the queue.
 *
 * The on/off switch only works while the provider is the mock one, i.e. in local / demo setups.
 * With a real map provider configured there is nothing to switch, and the call is refused.
 */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Admin", description = "System status, and the demo switch for the mock map service")
public class AdminController {

    private final RoutingProvider provider;
    private final CircuitBreaker breaker;
    private final RouteCache cache;
    private final JobRepository jobs;

    public AdminController(RoutingProvider routingProvider, CircuitBreaker routingCircuitBreaker, RouteCache routeCache,
            JobRepository jobs) {
        this.provider = routingProvider;
        this.breaker = routingCircuitBreaker;
        this.cache = routeCache;
        this.jobs = jobs;
    }

    public record RoutingStatus(String provider, Boolean providerDown, String breaker, long cachedRoutes,
            double cacheHitRatio) {
    }

    public record JobStatus(long pending, long running, long failedLast24h, double oldestPendingSeconds) {
    }

    public record SystemStatus(RoutingStatus routing, JobStatus jobs) {
    }

    public record MockSwitch(@NotNull Boolean down) {
    }

    @GetMapping("/status")
    @Operation(summary = "Routing health (breaker, cache) and the compute queue")
    public SystemStatus status() {
        Boolean down = provider instanceof MockRoutingProvider mock ? mock.isDown() : null;
        RoutingStatus routing = new RoutingStatus(provider.name(), down, breaker.getState().name(), cache.size(),
                cache.hitRate());
        JobStatus queue = new JobStatus(jobs.countByStatus("PENDING"), jobs.countByStatus("RUNNING"),
                jobs.countFailedLastDay(), jobs.oldestPendingAgeSeconds());
        return new SystemStatus(routing, queue);
    }

    @PutMapping("/routing/mock")
    @Operation(summary = "Switch the mock map service off or on (demo only)",
            description = "Off: new gaps fall back to straight line x 1.4 and trips come out DEGRADED. "
                    + "After 5 failures the circuit breaker opens. Back on: DEGRADED trips are recomputed by the recovery scan.")
    public SystemStatus switchMock(@Valid @RequestBody MockSwitch request) {
        if (!(provider instanceof MockRoutingProvider mock)) {
            throw new InvalidRequestException("Only the mock routing provider can be switched off");
        }
        mock.setDown(request.down());
        return status();
    }
}
