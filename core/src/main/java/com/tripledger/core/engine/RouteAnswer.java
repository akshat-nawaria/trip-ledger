package com.tripledger.core.engine;

import com.tripledger.core.geo.Distance;
import java.util.Objects;

/**
 * What the routing side says about a gap: either "here's the driving distance"
 * or "can't tell you right now" (provider down, breaker open, timeout...).
 * The engine never sees exceptions from the network, just one of these two.
 */
public sealed interface RouteAnswer {

    record Found(Distance drivingDistance) implements RouteAnswer {
        public Found {
            Objects.requireNonNull(drivingDistance, "drivingDistance");
        }
    }

    record Unavailable(String reason) implements RouteAnswer {
    }

    static RouteAnswer found(Distance drivingDistance) {
        return new Found(drivingDistance);
    }

    static RouteAnswer unavailable(String reason) {
        return new Unavailable(reason);
    }
}
