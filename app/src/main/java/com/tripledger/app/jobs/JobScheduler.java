package com.tripledger.app.jobs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The timers. Kept separate from the worker on purpose: tests switch the timers off
 * (tripledger.jobs.enabled=false) and call the worker and the scanner directly, so nothing
 * runs at a random moment in the middle of a test.
 *
 * If one run throws, Spring logs it and the next run still happens - the loop never dies.
 */
@Component
@ConditionalOnProperty(prefix = "tripledger.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobScheduler {

    private final ComputeWorker worker;
    private final RecoveryScanner recovery;

    public JobScheduler(ComputeWorker worker, RecoveryScanner recovery) {
        this.worker = worker;
        this.recovery = recovery;
    }

    @Scheduled(initialDelayString = "${tripledger.jobs.poll-millis}", fixedDelayString = "${tripledger.jobs.poll-millis}")
    public void work() {
        // keep going while there's a backlog, then wait for the next tick
        while (worker.runOnce() > 0) {
            // nothing else to do here
        }
    }

    @Scheduled(initialDelayString = "${tripledger.jobs.recovery-scan-millis}",
            fixedDelayString = "${tripledger.jobs.recovery-scan-millis}")
    public void recover() {
        recovery.scanOnce();
    }
}
