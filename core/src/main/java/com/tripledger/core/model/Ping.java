package com.tripledger.core.model;

import java.time.Instant;
import java.util.Objects;

/**
 * One GPS fix from the cab.
 *
 * accuracyMetres is the device's own guess of how far off it might be - bigger = worse.
 * deviceSeq is only there to break ties when two pings have the same timestamp.
 */
public record Ping(Instant timestamp, GeoPoint point, int accuracyMetres, long deviceSeq) {

    public Ping {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(point, "point");
        if (accuracyMetres < 0) {
            throw new IllegalArgumentException("accuracyMetres must be >= 0 but was " + accuracyMetres);
        }
    }
}
