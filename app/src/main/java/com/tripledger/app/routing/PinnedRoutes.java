package com.tripledger.app.routing;

import com.tripledger.core.engine.RouteAnswer;
import com.tripledger.core.engine.RouteLookup;
import com.tripledger.core.geo.Distance;
import com.tripledger.core.model.GeoPoint;
import com.tripledger.core.model.RouteEvidence;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sits in front of the normal routing for ONE trip: if an earlier run of this trip already paid
 * for the road distance of exactly this gap, that answer is replayed - no cache, no provider.
 *
 * Why: once a trip is billed, its km must not drift just because the map provider changed a road
 * next month, or because the provider happens to be down during a recompute. Only gaps that are
 * genuinely new (new evidence changed where a gap starts or ends) go to the provider.
 */
public final class PinnedRoutes implements RouteLookup {

    private record Leg(GeoPoint from, GeoPoint to) {
    }

    private final Map<Leg, Distance> pinned = new HashMap<>();
    private final RouteLookup fallback;
    private int replayed;

    // routes newest first: if a gap was routed more than once, the latest answer wins
    public PinnedRoutes(List<RouteEvidence> routes, RouteLookup fallback) {
        routes.forEach(r -> pinned.putIfAbsent(new Leg(r.from(), r.to()), r.drivingDistance()));
        this.fallback = fallback;
    }

    @Override
    public RouteAnswer route(GeoPoint from, GeoPoint to) {
        Distance known = pinned.get(new Leg(from, to));
        if (known != null) {
            replayed++;
            return RouteAnswer.found(known);
        }
        return fallback.route(from, to);
    }

    public int replayed() {
        return replayed;
    }
}
