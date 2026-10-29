package com.tripledger.core.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

/**
 * One point on the rebuilt route - either a cleaned GPS ping or an anchor.
 * anchor is null for plain pings.
 */
public record TrackPoint(Instant time, GeoPoint point, Anchor anchor) {

    // time first. If a ping and an anchor share a timestamp, ping goes first, then anchors by kind.
    // The last two tie-breaks only exist so the order never depends on input order.
    public static final Comparator<TrackPoint> IN_ORDER = Comparator
            .comparing(TrackPoint::time)
            .thenComparingInt(TrackPoint::sortRank)
            .thenComparingLong(p -> p.point().latE6())
            .thenComparingLong(p -> p.point().lonE6());

    public TrackPoint {
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(point, "point");
    }

    public static TrackPoint of(Ping ping) {
        return new TrackPoint(ping.timestamp(), ping.point(), null);
    }

    public static TrackPoint of(Anchor anchor) {
        return new TrackPoint(anchor.time(), anchor.point(), anchor);
    }

    public boolean isAnchor() {
        return anchor != null;
    }

    private int sortRank() {
        return anchor == null ? 0 : 1 + anchor.kind().ordinal();
    }
}
