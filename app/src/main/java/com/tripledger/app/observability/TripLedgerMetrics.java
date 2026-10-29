package com.tripledger.app.observability;

import com.tripledger.app.routing.RoutingUsage;
import com.tripledger.app.service.StoredEvidence;
import com.tripledger.core.model.SegmentLabel;
import com.tripledger.core.model.TripDistance;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Every business number we count, in one place, so the names stay consistent
 * (they end up as Prometheus metrics, e.g. tripledger_trips_computed_total).
 *
 * Deliberately no tenant or trip id as a tag: that would create one time series per trip and
 * eventually choke Prometheus. Per-trip detail lives in the database and the logs, not in metrics.
 *
 *   tripledger.trips.computed{status}      COMPUTED / DEGRADED results saved
 *   tripledger.trips.reused                computations skipped because nothing changed (fingerprint)
 *   tripledger.compute.duration            how long one computation takes (with histogram for p95/p99)
 *   tripledger.segments{rule}              pieces measured by each rule - REJECTED_* = rejections
 *   tripledger.routing.lookups{source}     road distances by where they came from: cache / provider / replayed
 *   tripledger.routing.unavailable         gaps that fell back to straight line x 1.4
 *   tripledger.ingest.uploads{result}      accepted / replayed uploads
 *   tripledger.ingest.pings{result}        new / duplicate pings
 *   tripledger.jobs.finished{outcome}      done / retry / failed
 *   tripledger.adjustments.created         signed corrections to billed trips
 */
@Component
public class TripLedgerMetrics {

    private final MeterRegistry registry;
    private final Timer computeDuration;

    public TripLedgerMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.computeDuration = Timer.builder("tripledger.compute.duration")
                .description("Time to compute one trip, routing included")
                .publishPercentileHistogram()
                .register(registry);
    }

    public Timer.Sample startCompute() {
        return Timer.start(registry);
    }

    public void computed(Timer.Sample sample, TripDistance result, RoutingUsage usage, int routesReplayed) {
        sample.stop(computeDuration);
        registry.counter("tripledger.trips.computed", "status", result.status().name()).increment();
        for (SegmentLabel label : SegmentLabel.values()) {
            long count = result.segmentsWith(label);
            if (count > 0) {
                registry.counter("tripledger.segments", "rule", label.name()).increment(count);
            }
        }
        lookups("cache", usage.cacheHits());
        lookups("provider", usage.providerCalls());
        lookups("replayed", routesReplayed);
        if (usage.unavailable() > 0) {
            registry.counter("tripledger.routing.unavailable").increment(usage.unavailable());
        }
    }

    public void reused() {
        registry.counter("tripledger.trips.reused").increment();
    }

    public void uploaded(StoredEvidence stored) {
        registry.counter("tripledger.ingest.uploads", "result", stored.replayed() ? "replayed" : "accepted").increment();
        if (!stored.replayed()) {
            registry.counter("tripledger.ingest.pings", "result", "new").increment(stored.pingsNew());
            registry.counter("tripledger.ingest.pings", "result", "duplicate").increment(stored.pingsDuplicate());
        }
    }

    public void jobFinished(String outcome) {
        registry.counter("tripledger.jobs.finished", "outcome", outcome).increment();
    }

    public void adjustmentCreated() {
        registry.counter("tripledger.adjustments.created").increment();
    }

    private void lookups(String source, int count) {
        if (count > 0) {
            registry.counter("tripledger.routing.lookups", "source", source).increment(count);
        }
    }
}
