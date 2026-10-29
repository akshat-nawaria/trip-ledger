package com.tripledger.app.jobs;

import com.tripledger.app.observability.TripLedgerMetrics;
import com.tripledger.app.persistence.JobRepository;
import com.tripledger.app.persistence.JobRepository.ClaimedJob;
import com.tripledger.app.service.DistanceService;
import com.tripledger.app.service.TripNotFoundException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Runs compute jobs. Two ways in:
 *
 *   runNow(trip)  - straight after an upload / a recompute request, so the caller usually gets the
 *                   km in the same response
 *   runOnce()     - the background loop (JobScheduler, every couple of seconds): picks up whatever is
 *                   due - retries, recovery jobs, and jobs whose worker crashed half way (lease ran out)
 *
 * Every job ends one of three ways: DONE, back in the queue for a later retry, or FAILED for good.
 * A compute that throws never takes the request or the loop down with it.
 */
@Component
public class ComputeWorker {

    private static final Logger log = LoggerFactory.getLogger(ComputeWorker.class);

    private final JobRepository jobs;
    private final DistanceService distance;
    private final JobSettings settings;
    private final TripLedgerMetrics metrics;

    public ComputeWorker(JobRepository jobs, DistanceService distance, JobSettings settings, TripLedgerMetrics metrics) {
        this.jobs = jobs;
        this.distance = distance;
        this.settings = settings;
        this.metrics = metrics;
    }

    // returns how many jobs it handled (0 = nothing was due)
    public int runOnce() {
        List<ClaimedJob> claimed = jobs.claim(settings.batchSize(), settings.lease());
        claimed.forEach(this::execute);
        return claimed.size();
    }

    // empty = there was no waiting job for this trip, or another worker is on it right now
    public Optional<DistanceService.Outcome> runNow(String tenant, String tripId) {
        return jobs.claimForTrip(tenant, tripId, settings.lease()).flatMap(this::execute);
    }

    private Optional<DistanceService.Outcome> execute(ClaimedJob job) {
        // every log line written while this job runs carries the tenant and trip (JSON logs pick these up)
        MDC.put("tenantId", job.tenantId());
        MDC.put("tripId", job.tripId());
        try {
            DistanceService.Outcome outcome = distance.compute(job.tenantId(), job.tripId());
            jobs.complete(job.id());
            metrics.jobFinished("done");
            return Optional.of(outcome);
        } catch (TripNotFoundException e) {
            jobs.fail(job.id(), "trip not found"); // retrying can't make a missing trip appear
            metrics.jobFinished("failed");
        } catch (RuntimeException e) {
            // only the exception type is stored and logged with the ids - messages can carry data we don't keep
            String error = e.getClass().getSimpleName();
            if (job.attempts() >= settings.maxAttempts()) {
                jobs.fail(job.id(), error);
                metrics.jobFinished("failed");
                log.error("Compute job {} for trip {} failed for good after {} attempts ({})",
                        job.id(), job.tripId(), job.attempts(), error, e);
            } else {
                jobs.retryLater(job.id(), settings.backoff(job.attempts()), error);
                metrics.jobFinished("retry");
                log.warn("Compute job {} for trip {} failed ({}), will retry", job.id(), job.tripId(), error);
            }
        } finally {
            MDC.remove("tenantId");
            MDC.remove("tripId");
        }
        return Optional.empty();
    }
}
