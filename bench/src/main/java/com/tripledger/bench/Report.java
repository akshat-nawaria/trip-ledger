package com.tripledger.bench;

import com.tripledger.core.model.SegmentLabel;
import com.tripledger.core.model.TripStatus;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// turns the two runs into the markdown that goes into bench/results.md
record Report(Benchmark.Settings settings, Machine machine, Benchmark.Pass steady, Benchmark.OutageRun outage) {

    String toMarkdown() {
        StringBuilder md = new StringBuilder();
        Benchmark.Pass s = steady;
        Benchmark.Pass o = outage.pass;
        md.append("# TripLedger benchmark\n\n");
        md.append("Run on ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n\n");

        md.append("## Machine\n\n| | |\n|---|---|\n");
        row(md, "OS", machine.os());
        row(md, "CPU", machine.cpu() + ", " + machine.cores() + " logical cores");
        row(md, "RAM", gb(machine.ramBytes()));
        row(md, "Java", machine.java());
        row(md, "Heap limit (-Xmx)", mb(machine.maxHeapBytes()));

        md.append("\n## Workload\n\n| | |\n|---|---|\n");
        row(md, "Trips", n(s.trips()) + " (" + n(settings.routes()) + " recurring cab routes, seed " + settings.seed() + ")");
        row(md, "Pings", n(s.pings) + " in total, " + n(s.pings / s.trips()) + " per trip on average (min "
                + n(s.minPings) + ", max " + n(s.maxPings) + ")");
        row(md, "Pings kept after cleaning", n(s.pingsKept) + " (" + pct(s.pingsKept, s.pings) + ")");
        row(md, "Billable km, all trips", String.format(Locale.US, "%,.2f", s.totalKmTimes100() / 100.0));

        md.append("\n## 1. One thread, map service healthy\n\n| | |\n|---|---|\n");
        row(md, "Engine time, all trips", seconds(s.engineNanosSum()));
        row(md, "Per trip p50 / p95 / p99 / max", ms(s.percentileNanos(50)) + " / " + ms(s.percentileNanos(95)) + " / "
                + ms(s.percentileNanos(99)) + " / " + ms(s.percentileNanos(100)));
        row(md, "One core computes", n(perSecond(s.trips(), s.engineNanosSum())) + " trips/s = "
                + n(perSecond(s.pings, s.engineNanosSum())) + " pings/s");
        row(md, "Wall time incl. making the trips", seconds(s.wallNanos));
        row(md, "Peak heap used", mb(s.peakHeapBytes));
        row(md, "Heap still in use after the run", mb(s.liveHeapBytes) + " (route cache: " + n(s.cachedRoutes) + " roads)");
        row(md, "Garbage collections", n(s.gcRuns) + " runs, " + n(s.gcMillis) + " ms in total");
        row(md, "Trips COMPUTED / DEGRADED", n(s.countStatus(TripStatus.COMPUTED)) + " / " + n(s.countStatus(TripStatus.DEGRADED)));

        md.append("\n### Segments by rule\n\n| Rule | Segments | Share |\n|---|---:|---:|\n");
        for (SegmentLabel label : SegmentLabel.values()) {
            long count = s.labels.getOrDefault(label, 0L);
            md.append("| ").append(label.name()).append(" | ").append(n(count)).append(" | ").append(pct(count, s.segments)).append(" |\n");
        }
        md.append("\nFlagged for review: ").append(n(s.reviewFlags)).append(" segments (")
                .append(String.format(Locale.US, "%.2f", s.reviewFlags / (double) s.trips())).append(" per trip).\n");

        md.append("\n## 2. Routing calls (R15)\n\n| Approach | Calls to the map service | Per trip |\n|---|---:|---:|\n");
        md.append("| Route every pair of points | ").append(n(s.segments)).append(" | ").append(perTrip(s.segments, s.trips())).append(" |\n");
        md.append("| Rules first, route only real gaps, no cache | ").append(n(s.requests)).append(" | ").append(perTrip(s.requests, s.trips())).append(" |\n");
        md.append("| Rules first + snapped-cell cache (as built) | ").append(n(s.providerCalls)).append(" | ").append(perTrip(s.providerCalls, s.trips())).append(" |\n");
        md.append("\nCache hits: ").append(n(s.cacheHits)).append(" of ").append(n(s.requests)).append(" lookups (")
                .append(pct(s.cacheHits, s.requests)).append(").\n");

        md.append("\n## 3. ").append(settings.threads()).append(" threads, map service fails halfway\n\n| | |\n|---|---|\n");
        row(md, "Wall time, all trips", seconds(o.wallNanos) + " = " + n(perSecond(o.trips(), o.wallNanos)) + " trips/s, "
                + n(perSecond(o.pings, o.wallNanos)) + " pings/s (incl. making the trips)");
        row(md, "Peak heap used", mb(o.peakHeapBytes));
        row(md, "Map service off", "after trip " + n(outage.downAt) + ", back on after trip " + n(outage.upAt));
        row(md, "Trips computed while it was off", n(outage.ranWhileDown) + ", of which " + n(outage.savedByCache)
                + " still COMPUTED (every road they needed was already cached)");
        row(md, "Calls that reached the dead service", n(outage.callsToDeadProvider)
                + " (then the breaker opened and stopped calling)");
        row(md, "Lookups the open breaker answered without calling", n(Math.max(0, o.unavailable - outage.callsToDeadProvider)));
        int whileOff = outage.ranWhileDown - outage.savedByCache;
        row(md, "Trips DEGRADED (straight line x 1.4)", n(outage.degradedAfterPass) + ": " + n(whileOff)
                + " while it was off, " + n(Math.max(0, outage.degradedAfterPass - whileOff))
                + " right after it came back, while the breaker was still waiting");
        row(md, "Breaker", outage.breakerTransitions.isEmpty() ? "never opened" : String.join(", ", outage.breakerTransitions));
        row(md, "Recovery (like RecoveryScanner)", seconds(outage.recoveryNanos) + ", " + n(outage.recoveryCalls) + " calls, "
                + (outage.trialRounds == 0 ? "breaker already CLOSED again by then"
                        : n(outage.trialRounds) + " trial round(s) while HALF_OPEN"));
        row(md, "Trips COMPUTED at the end", n(outage.computedAtEnd) + " of " + n(o.trips()));
        row(md, "km identical to run 1", n(o.trips() - outage.kmMismatches) + " of " + n(o.trips())
                + (outage.kmMismatches == 0 ? " (every trip)" : " - MISMATCH"));
        md.append("\nBreaker wait-in-open is ").append(settings.breakerWait().toMillis())
                .append(" ms here instead of 30 s: the benchmark squeezes an evening of trips into seconds.\n");
        return md.toString();
    }

    private static void row(StringBuilder md, String name, String value) {
        md.append("| ").append(name).append(" | ").append(value).append(" |\n");
    }

    private static String n(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private static String pct(long part, long whole) {
        return whole == 0 ? "-" : String.format(Locale.US, "%.2f %%", 100.0 * part / whole);
    }

    private static String perTrip(long total, int trips) {
        return String.format(Locale.US, "%,.2f", total / (double) trips);
    }

    private static long perSecond(long count, long nanos) {
        return nanos == 0 ? 0 : Math.round(count / (nanos / 1e9));
    }

    private static String ms(long nanos) {
        return String.format(Locale.US, "%.2f ms", nanos / 1e6);
    }

    private static String seconds(long nanos) {
        return String.format(Locale.US, "%.1f s", nanos / 1e9);
    }

    private static String mb(long bytes) {
        return String.format(Locale.US, "%,d MB", bytes / (1024 * 1024));
    }

    private static String gb(long bytes) {
        return bytes == 0 ? "unknown" : String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
