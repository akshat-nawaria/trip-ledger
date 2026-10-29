package com.tripledger.app.jobs;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * tripledger.jobs.* in application.yml.
 *
 *   enabled          - run the background worker + recovery scan on a timer (tests switch it off and drive them by hand)
 *   pollMillis       - how often the worker looks for due jobs
 *   batchSize        - how many jobs it takes per look
 *   lease            - how long a claimed job is "ours". If the worker dies, the job is free again after this
 *   maxAttempts      - give up (FAILED) after this many tries
 *   retryBase        - wait before the 1st retry; tripled for each further one (capped at retryMax)
 *   recoveryScanMillis / recoveryBatch - how often, and how many, DEGRADED trips are re-queued once routing works
 */
@ConfigurationProperties(prefix = "tripledger.jobs")
public record JobSettings(
        boolean enabled,
        long pollMillis,
        int batchSize,
        Duration lease,
        int maxAttempts,
        Duration retryBase,
        Duration retryMax,
        long recoveryScanMillis,
        int recoveryBatch) {

    public JobSettings {
        Objects.requireNonNull(lease, "tripledger.jobs.lease");
        Objects.requireNonNull(retryBase, "tripledger.jobs.retry-base");
        Objects.requireNonNull(retryMax, "tripledger.jobs.retry-max");
        if (batchSize < 1 || maxAttempts < 1 || recoveryBatch < 1) {
            throw new IllegalArgumentException("tripledger.jobs batch-size, max-attempts and recovery-batch must be >= 1");
        }
    }

    // 1st retry after retryBase, then x3 each time: 10 s, 30 s, 90 s, 270 s ... never more than retryMax
    public Duration backoff(int attemptsSoFar) {
        Duration wait = retryBase;
        for (int i = 1; i < attemptsSoFar && wait.compareTo(retryMax) < 0; i++) {
            wait = wait.multipliedBy(3);
        }
        return wait.compareTo(retryMax) > 0 ? retryMax : wait;
    }
}
