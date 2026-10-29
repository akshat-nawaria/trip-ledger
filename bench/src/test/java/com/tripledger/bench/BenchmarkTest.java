package com.tripledger.bench;

import static org.assertj.core.api.Assertions.assertThat;

import com.tripledger.core.model.TripStatus;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * A small version of the real benchmark (400 trips instead of 10,000), so CI proves on every push
 * that the outage story holds: the map service dies, trips degrade, the breaker opens, and after
 * recovery every trip is COMPUTED with exactly the km of the run that never had an outage.
 */
class BenchmarkTest {

    private static final Benchmark.Settings SMALL =
            new Benchmark.Settings(400, 2, 40, 20_261_029L, 0.5, 0.1, Duration.ofMillis(200));

    @Test
    void anOutageHalfwayIsFullyRecoveredWithIdenticalKm() throws Exception {
        Report report = Benchmark.run(SMALL, false);
        Benchmark.OutageRun outage = report.outage();

        assertThat(outage.degradedAfterPass).isGreaterThan(0);          // the outage really hurt
        assertThat(outage.breakerTransitions).contains("CLOSED_TO_OPEN"); // and the breaker reacted
        assertThat(outage.computedAtEnd).isEqualTo(400);                  // recovery brought every trip back
        assertThat(outage.kmMismatches).isEqualTo(0);                     // to exactly the healthy-run km
    }

    @Test
    void theCacheAndTheRulesSaveMostCalls() throws Exception {
        Benchmark.Pass steady = Benchmark.run(SMALL, false).steady();

        assertThat(steady.countStatus(TripStatus.COMPUTED)).isEqualTo(400);
        assertThat(steady.requests).isLessThan(steady.segments / 100);   // rules: far under 1% of pairs need a road
        assertThat(steady.providerCalls).isLessThanOrEqualTo(steady.requests);
    }

    @Test
    void theReportHasEverySection() throws Exception {
        String markdown = Benchmark.run(SMALL, false).toMarkdown();

        assertThat(markdown).contains("## Machine", "## 1. One thread", "## 2. Routing calls (R15)",
                "## 3. 2 threads, map service fails halfway", "km identical to run 1");
    }
}
