package com.tripledger.core.support;

import com.tripledger.core.engine.RouteAnswer;
import com.tripledger.core.engine.RouteLookup;
import com.tripledger.core.geo.Distance;
import com.tripledger.core.geo.Haversine;
import com.tripledger.core.model.GeoPoint;
import java.util.HashMap;
import java.util.Map;

/**
 * Stand-in for the map service in tests. No network, fully predictable.
 *
 * By default the "road" is the straight line x 1.3. You can pin exact answers for specific
 * gaps, or switch it off completely to pretend the provider is down. It also counts calls,
 * which is how the tests prove short gaps never cost a routing call.
 */
public final class FakeRouteLookup implements RouteLookup {

    private final Map<String, RouteAnswer> pinned = new HashMap<>();
    private double roadFactor = 1.3;
    private boolean down;
    private int calls;

    public static FakeRouteLookup working() {
        return new FakeRouteLookup();
    }

    public static FakeRouteLookup down() {
        FakeRouteLookup lookup = new FakeRouteLookup();
        lookup.down = true;
        return lookup;
    }

    public FakeRouteLookup roadFactor(double factor) {
        this.roadFactor = factor;
        return this;
    }

    public FakeRouteLookup answer(GeoPoint from, GeoPoint to, Distance driving) {
        pinned.put(key(from, to), RouteAnswer.found(driving));
        return this;
    }

    public int calls() {
        return calls;
    }

    @Override
    public RouteAnswer route(GeoPoint from, GeoPoint to) {
        calls++;
        if (down) {
            return RouteAnswer.unavailable("fake provider is down");
        }
        RouteAnswer fixed = pinned.get(key(from, to));
        if (fixed != null) {
            return fixed;
        }
        return RouteAnswer.found(Distance.ofMetres(Haversine.metresBetween(from, to) * roadFactor));
    }

    private static String key(GeoPoint from, GeoPoint to) {
        return from.latE6() + "," + from.lonE6() + "->" + to.latE6() + "," + to.lonE6();
    }
}
