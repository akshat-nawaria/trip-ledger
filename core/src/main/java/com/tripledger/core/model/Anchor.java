package com.tripledger.core.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A point we know the cab passed through, even if GPS was dead at the time.
 *
 * This is what fixes the underpass problem: the GPS went quiet for 22 minutes, but we still
 * know the cab dropped someone at a specific house at 10:12, so the route has to go through there.
 */
public record Anchor(AnchorKind kind, String employeeRef, GeoPoint point, Instant time, AnchorSource source) {

    public Anchor {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(source, "source");
    }
}
