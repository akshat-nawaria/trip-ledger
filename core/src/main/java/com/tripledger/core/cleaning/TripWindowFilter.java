package com.tripledger.core.cleaning;

import com.tripledger.core.model.Ping;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Drops pings stamped way outside the trip window.
 *
 * Phones with a wrong clock happily send pings from 2019 or from tomorrow. A bit of slack
 * (the tolerance) is fine since clocks drift a little, but anything beyond that can't
 * belong to this trip.
 */
public final class TripWindowFilter implements PingFilter {

    public static final String NAME = "outside-trip-window";

    private final Instant earliest;
    private final Instant latest;

    public TripWindowFilter(Instant windowStart, Instant windowEnd, Duration tolerance) {
        this.earliest = windowStart.minus(tolerance);
        this.latest = windowEnd.plus(tolerance);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Ping> apply(List<Ping> pings) {
        return pings.stream()
                .filter(p -> !p.timestamp().isBefore(earliest) && !p.timestamp().isAfter(latest))
                .toList();
    }
}
