package com.tripledger.app.routing;

import com.tripledger.core.geo.Distance;
import com.tripledger.core.geo.Haversine;
import com.tripledger.core.model.GeoPoint;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A pretend map service for local runs, demos and tests.
 *
 * The "road" is just the straight line times a road factor (1.3 by default, city roads usually
 * come out 20-40% longer than the crow flies). Same question, same answer, no network needed.
 *
 * It has an off switch too: setDown(true) makes every call fail. That's how I show the circuit
 * breaker and the DEGRADED fallback in a demo without actually breaking anything.
 */
public final class MockRoutingProvider implements RoutingProvider {

    public static final String NAME = "mock";

    private final double roadFactor;
    private final AtomicLong calls = new AtomicLong();
    private volatile boolean down;

    public MockRoutingProvider(double roadFactor) {
        // a road can't be shorter than the straight line, so anything under 1.0 is a config mistake
        if (!(roadFactor >= 1.0)) {
            throw new IllegalArgumentException("road factor must be at least 1.0 but was " + roadFactor);
        }
        this.roadFactor = roadFactor;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Distance drivingDistance(GeoPoint from, GeoPoint to) {
        // counted even when we're "down" - a failed request still went out, a real provider would see it
        calls.incrementAndGet();
        if (down) {
            throw new RoutingException("mock routing provider is switched off");
        }
        return Distance.ofMetres(Haversine.metresBetween(from, to) * roadFactor);
    }

    public void setDown(boolean down) {
        this.down = down;
    }

    public boolean isDown() {
        return down;
    }

    // how many times someone actually asked us (i.e. what we'd be paying for with a real provider)
    public long calls() {
        return calls.get();
    }
}
