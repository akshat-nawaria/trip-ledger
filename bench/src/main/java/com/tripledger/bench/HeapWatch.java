package com.tripledger.bench;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * Watches memory while a scenario runs: samples the used heap every 10 ms and keeps the highest
 * value, and counts garbage collections. Sampling misses peaks shorter than 10 ms, which is fine
 * for "does 10,000 trips fit in 1 GB" - and far more honest than adding up per-pool peaks that
 * happened at different moments.
 */
final class HeapWatch implements AutoCloseable {

    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    private final Thread sampler;
    private final long gcCountBefore;
    private final long gcMillisBefore;
    private volatile boolean running = true;
    private volatile long peakBytes;

    private HeapWatch() {
        this.gcCountBefore = gcCount();
        this.gcMillisBefore = gcTotalMillis();
        this.sampler = Thread.ofPlatform().daemon().name("heap-watch").start(() -> {
            while (running) {
                long used = memory.getHeapMemoryUsage().getUsed();
                if (used > peakBytes) {
                    peakBytes = used;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
    }

    // starts from a clean heap so one scenario's garbage doesn't count against the next
    static HeapWatch start() {
        System.gc();
        return new HeapWatch();
    }

    long peakBytes() {
        return peakBytes;
    }

    long gcRuns() {
        return gcCount() - gcCountBefore;
    }

    long gcMillisSpent() {
        return gcTotalMillis() - gcMillisBefore;
    }

    private static long gcTotalMillis() {
        long total = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0, gc.getCollectionTime());
        }
        return total;
    }

    // what is still alive after the run (route cache, result arrays) once the garbage is gone
    long liveBytesAfterGc() {
        System.gc();
        return memory.getHeapMemoryUsage().getUsed();
    }

    private static long gcCount() {
        long total = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0, gc.getCollectionCount());
        }
        return total;
    }

    @Override
    public void close() {
        running = false;
        sampler.interrupt();
    }
}
