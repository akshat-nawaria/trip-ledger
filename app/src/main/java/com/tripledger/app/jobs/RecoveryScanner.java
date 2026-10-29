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
 * "Works again" = the circuit breaker is CLOSED. While it's OPEN or testing (HALF_OPEN) we don't
 * bother - every recompute would just come out DEGRADED again and waste a job.
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
        if (routingBreaker.getState() != CircuitBreaker.State.CLOSED) {
            return 0;
        }
        int queued = jobs.enqueueRecovery(settings.recoveryBatch());
        if (queued > 0) {
            log.info("Routing is healthy: queued {} trips for recompute", queued);
        }
        return queued;
    }
}
