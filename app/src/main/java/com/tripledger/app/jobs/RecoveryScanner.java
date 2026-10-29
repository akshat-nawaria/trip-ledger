package com.tripledger.app.jobs;

import com.tripledger.app.persistence.JobRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Brings DEGRADED trips back (R10). Trips computed while routing was down got the straight line
 * x 1.4 as a provisional answer; once routing works again they're queued for a proper recompute.
 *
 * How hard we push depends on the circuit breaker:
 *
 *   OPEN       provider just failed, leave it alone          -> queue nothing
 *   HALF_OPEN  the wait is over, provider gets a few trials  -> queue a handful of trips (as many
 *              as the breaker lets through), so THEIR routing calls are the trial calls
 *   CLOSED     provider is healthy                           -> queue a full batch
 *
 * The HALF_OPEN step matters when nobody is uploading. Without it the breaker only ever closes
 * when a new trip happens to need a route, so a quiet night could leave DEGRADED trips stuck.
 */
@Component
public class RecoveryScanner {

    private static final Logger log = LoggerFactory.getLogger(RecoveryScanner.class);

    private final JobRepository jobs;
    private final CircuitBreaker routingBreaker;
    private final JobSettings settings;

    public RecoveryScanner(JobRepository jobs, CircuitBreaker routingCircuitBreaker, JobSettings settings) {
        this.jobs = jobs;
        this.routingBreaker = routingCircuitBreaker;
        this.settings = settings;
    }

    // returns how many trips were queued
    public int scanOnce() {
        CircuitBreaker.State state = routingBreaker.getState();
        if (state == CircuitBreaker.State.CLOSED) {
            int queued = jobs.enqueueRecovery(settings.recoveryBatch());
            if (queued > 0) {
                log.info("Routing is healthy: queued {} trips for recompute", queued);
            }
            return queued;
        }
        if (state == CircuitBreaker.State.HALF_OPEN) {
            int trials = routingBreaker.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState();
            int queued = jobs.enqueueRecovery(Math.min(trials, settings.recoveryBatch()));
            if (queued > 0) {
                log.info("Routing is being tested: queued {} trips as trial runs", queued);
            }
            return queued;
        }
        return 0;
    }
}
