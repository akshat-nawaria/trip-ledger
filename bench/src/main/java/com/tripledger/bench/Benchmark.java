package com.tripledger.bench;

import com.tripledger.app.routing.RoutingUsage;
import com.tripledger.app.routing.TripRouting;
import com.tripledger.core.config.BillingRules;
import com.tripledger.core.engine.DistanceEngine;
import com.tripledger.core.model.Segment;
import com.tripledger.core.model.SegmentLabel;
import com.tripledger.core.model.TripDistance;
import com.tripledger.core.model.TripEvidence;
import com.tripledger.core.model.TripStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * R16: 10,000 trips x ~1,500 pings, run time + memory, and the map service dying halfway.
 *
 * Two runs over the very same trips:
 *
 *   1. one thread, map service healthy the whole time
 *      -> per-trip latency (p50/p95/p99), memory, routing calls with and without the cache (R15),
 *         and the km of every trip, kept as the reference answer
 *
 *   2. every core busy, map service switched off when half the trips are done and back on 10% later
 *      -> throughput, what the outage did (DEGRADED trips, breaker), then recovery exactly like the
 *         app's RecoveryScanner does it: wait while OPEN, a few trial trips while HALF_OPEN, the
 *         rest once CLOSED. At the end every trip must be COMPUTED with exactly the km of run 1.
 *
 * Run it:  mvn -q install -DskipTests   then   mvn -q -pl bench exec:exec
 * Results are printed and saved to bench/target/bench-results.md.
 */
public final class Benchmark {

    private static final BillingRules RULES = BillingRules.defaults();

    record Settings(int trips, int threads, int routes, long seed, double outageStartsAt, double outageShare,
            Duration breakerWait) {

        static Settings fromArgs(String[] args) {
            int trips = args.length > 0 ? Integer.parseInt(args[0]) : 10_000;
            int threads = args.length > 1 ? Integer.parseInt(args[1]) : Runtime.getRuntime().availableProcessors();
            return new Settings(trips, threads, 400, 20_261_029L, 0.5, 0.1, Duration.ofSeconds(1));
        }
    }

    public static void main(String[] args) throws Exception {
        Settings settings = Settings.fromArgs(args);
        Report report = run(settings, true);
        String markdown = report.toMarkdown();
        System.out.println(markdown);
        Path out = Path.of("target", "bench-results.md");
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, markdown);
            System.out.println("Saved to " + out.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("(could not save the report: " + e.getMessage() + ")");
        }
    }

    static Report run(Settings settings, boolean showProgress) throws Exception {
        TripFactory trips = new TripFactory(settings.seed(), settings.routes());
        Pass steady = steadyRun(settings, trips, showProgress);
        OutageRun outage = outageRun(settings, trips, steady, showProgress);
        return new Report(settings, Machine.describe(), steady, outage);
    }

    // ---- run 1: one thread, healthy map service -----------------------------------------------------

    private static Pass steadyRun(Settings settings, TripFactory trips, boolean showProgress) {
        RoutingChain chain = RoutingChain.fresh(settings.breakerWait());
        DistanceEngine engine = new DistanceEngine(RULES);
        Pass pass = new Pass(settings.trips());
        try (HeapWatch heap = HeapWatch.start()) {
            long start = System.nanoTime();
            for (int i = 0; i < settings.trips(); i++) {
                computeOne(i, trips, engine, chain, pass);
                if (showProgress && (i + 1) % 2_000 == 0) {
                    System.err.printf("  run 1: %,d / %,d trips%n", i + 1, settings.trips());
                }
            }
            pass.finish(System.nanoTime() - start, heap, chain);
        }
        return pass;
    }

    // ---- run 2: all cores, map service fails halfway ------------------------------------------------

    private static OutageRun outageRun(Settings settings, TripFactory trips, Pass reference, boolean showProgress)
            throws Exception {
        RoutingChain chain = RoutingChain.fresh(settings.breakerWait());
        DistanceEngine engine = new DistanceEngine(RULES);
        Pass pass = new Pass(settings.trips());
        OutageRun run = new OutageRun(pass);
        List<String> transitions = java.util.Collections.synchronizedList(new ArrayList<>());
        chain.breaker().getEventPublisher().onStateTransition(e -> transitions.add(e.getStateTransition().name()));

        int n = settings.trips();
        int downAt = (int) (n * settings.outageStartsAt());
        int upAt = downAt + (int) (n * settings.outageShare());
        AtomicInteger next = new AtomicInteger();
        AtomicInteger done = new AtomicInteger();
        AtomicLong callsWhenDown = new AtomicLong();
        AtomicLong callsWhenUp = new AtomicLong();
        AtomicInteger ranWhileDown = new AtomicInteger();
        AtomicInteger savedByCache = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(settings.threads());
        try (HeapWatch heap = HeapWatch.start()) {
            long start = System.nanoTime();
            List<Future<?>> workers = new ArrayList<>();
            for (int w = 0; w < settings.threads(); w++) {
                workers.add(pool.submit(() -> {
                    int i;
                    while ((i = next.getAndIncrement()) < n) {
                        boolean down = chain.provider().isDown();
                        TripStatus status = computeOne(i, trips, engine, chain, pass);
                        if (down) {
                            ranWhileDown.incrementAndGet();
                            if (status == TripStatus.COMPUTED) {
                                savedByCache.incrementAndGet();
                            }
                        }
                        int finished = done.incrementAndGet();
                        if (finished == downAt) {
                            callsWhenDown.set(chain.provider().calls());
                            chain.provider().setDown(true);
                        } else if (finished == upAt) {
                            callsWhenUp.set(chain.provider().calls());
                            chain.provider().setDown(false);
                        }
                        if (showProgress && finished % 2_000 == 0) {
                            System.err.printf("  run 2: %,d / %,d trips%n", finished, n);
                        }
                    }
                    return null;
                }));
            }
            for (Future<?> worker : workers) {
                worker.get();
            }
            pass.finish(System.nanoTime() - start, heap, chain);
        }
        run.downAt = downAt;
        run.upAt = upAt;
        run.ranWhileDown = ranWhileDown.get();
        run.savedByCache = savedByCache.get();
        run.callsToDeadProvider = callsWhenUp.get() - callsWhenDown.get();
        run.degradedAfterPass = pass.countStatus(TripStatus.DEGRADED);

        recover(run, chain, engine, trips, pool);
        pool.shutdown();
        run.breakerTransitions = List.copyOf(transitions);

        for (int i = 0; i < n; i++) {
            if (pass.totalMm[i] != reference.totalMm[i]) {
                run.kmMismatches++;
            }
        }
        run.computedAtEnd = pass.countStatus(TripStatus.COMPUTED);
        return run;
    }

    // same steps as the app's RecoveryScanner + ComputeWorker, minus the database
    private static void recover(OutageRun run, RoutingChain chain, DistanceEngine engine, TripFactory trips,
            ExecutorService pool) throws Exception {
        Pass pass = run.pass;
        Deque<Integer> pending = new ArrayDeque<>();
        for (int i = 0; i < pass.status.length; i++) {
            if (pass.status[i] == TripStatus.DEGRADED) {
                pending.add(i);
            }
        }
        long callsBefore = chain.provider().calls();
        long start = System.nanoTime();
        long giveUpAt = start + Duration.ofMinutes(2).toNanos();
        while (!pending.isEmpty() && System.nanoTime() < giveUpAt) {
            CircuitBreaker.State state = chain.breaker().getState();
            if (state == CircuitBreaker.State.HALF_OPEN) {
                run.trialRounds++;
                for (int t = 0; t < chain.trialCalls() && !pending.isEmpty(); t++) {
                    int i = pending.poll();
                    if (recomputeOne(i, trips, engine, chain, pass) == TripStatus.DEGRADED) {
                        pending.add(i);
                    }
                }
            } else if (state == CircuitBreaker.State.CLOSED) {
                List<Integer> batch = new ArrayList<>(pending);
                pending.clear();
                List<Future<TripStatus>> results = new ArrayList<>();
                for (int i : batch) {
                    results.add(pool.submit(() -> recomputeOne(i, trips, engine, chain, pass)));
                }
                for (int k = 0; k < batch.size(); k++) {
                    if (results.get(k).get() == TripStatus.DEGRADED) {
                        pending.add(batch.get(k));
                    }
                }
            } else {
                Thread.sleep(5); // OPEN: leave the provider alone until the breaker's wait is over
            }
        }
        run.recoveryNanos = System.nanoTime() - start;
        run.recoveryCalls = chain.provider().calls() - callsBefore;
    }

    // ---- one trip -----------------------------------------------------------------------------------

    private static TripStatus computeOne(int i, TripFactory trips, DistanceEngine engine, RoutingChain chain, Pass pass) {
        TripEvidence trip = trips.trip(i); // making the trip is not part of the measured time
        long start = System.nanoTime();
        TripRouting routing = chain.gateway().newTrip();
        TripDistance result = engine.compute(trip, routing);
        long nanos = System.nanoTime() - start;
        pass.record(i, trip, result, routing.usage(), nanos);
        return result.status();
    }

    private static TripStatus recomputeOne(int i, TripFactory trips, DistanceEngine engine, RoutingChain chain,
            Pass pass) {
        TripDistance result = engine.compute(trips.trip(i), chain.gateway().newTrip());
        pass.totalMm[i] = result.total().millimetres();
        pass.status[i] = result.status();
        return result.status();
    }

    // ---- what a run collects ------------------------------------------------------------------------

    static final class Pass {

        final long[] totalMm;
        final TripStatus[] status;
        final long[] engineNanos;
        final Map<SegmentLabel, Long> labels = new EnumMap<>(SegmentLabel.class);
        long pings;
        long pingsKept;
        int minPings = Integer.MAX_VALUE;
        int maxPings;
        long segments;
        long reviewFlags;
        long requests;
        long cacheHits;
        long providerCalls;
        long unavailable;
        long wallNanos;
        long peakHeapBytes;
        long liveHeapBytes;
        long gcRuns;
        long gcMillis;
        long cachedRoutes;

        Pass(int trips) {
            totalMm = new long[trips];
            status = new TripStatus[trips];
            engineNanos = new long[trips];
        }

        // trips finish on different threads; a lock per trip is nothing next to ~1 ms of work
        synchronized void record(int i, TripEvidence trip, TripDistance result, RoutingUsage usage, long nanos) {
            totalMm[i] = result.total().millimetres();
            status[i] = result.status();
            engineNanos[i] = nanos;
            int count = trip.pings().size();
            pings += count;
            pingsKept += result.cleaning().kept();
            minPings = Math.min(minPings, count);
            maxPings = Math.max(maxPings, count);
            segments += result.segments().size();
            for (Segment segment : result.segments()) {
                labels.merge(segment.label(), 1L, Long::sum);
                if (segment.label().needsReview()) {
                    reviewFlags++;
                }
            }
            requests += usage.requests();
            cacheHits += usage.cacheHits();
            providerCalls += usage.providerCalls();
            unavailable += usage.unavailable();
        }

        void finish(long wall, HeapWatch heap, RoutingChain chain) {
            wallNanos = wall;
            peakHeapBytes = heap.peakBytes();
            gcRuns = heap.gcRuns();
            gcMillis = heap.gcMillisSpent();
            liveHeapBytes = heap.liveBytesAfterGc();
            cachedRoutes = chain.cache().size();
        }

        int trips() {
            return totalMm.length;
        }

        int countStatus(TripStatus wanted) {
            int count = 0;
            for (TripStatus s : status) {
                if (s == wanted) {
                    count++;
                }
            }
            return count;
        }

        long totalKmTimes100() {
            long mm = 0;
            for (long trip : totalMm) {
                mm += trip;
            }
            return Math.round(mm / 10_000.0);
        }

        long engineNanosSum() {
            long sum = 0;
            for (long n : engineNanos) {
                sum += n;
            }
            return sum;
        }

        long percentileNanos(double p) {
            long[] sorted = engineNanos.clone();
            java.util.Arrays.sort(sorted);
            int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
            return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
        }
    }

    static final class OutageRun {

        final Pass pass;
        int downAt;
        int upAt;
        int ranWhileDown;
        int savedByCache;
        long callsToDeadProvider;
        int degradedAfterPass;
        int trialRounds;
        long recoveryNanos;
        long recoveryCalls;
        int computedAtEnd;
        int kmMismatches;
        List<String> breakerTransitions = List.of();

        OutageRun(Pass pass) {
            this.pass = pass;
        }
    }
}
