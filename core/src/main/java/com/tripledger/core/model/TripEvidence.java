package com.tripledger.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Everything we know about one trip - the input to the distance engine.
 *
 * The window is the period the trip was supposed to run in. Pings or milestones far
 * outside it are almost always from a phone with a wrong clock, so they get ignored.
 */
public record TripEvidence(
        String tripId,
        TripType tripType,
        Instant windowStart,
        Instant windowEnd,
        GeoPoint plannedOffice,
        List<Ping> pings,
        List<Milestone> milestones,
        List<PlannedStop> plannedStops) {

    public TripEvidence {
        Objects.requireNonNull(tripId, "tripId");
        Objects.requireNonNull(tripType, "tripType");
        Objects.requireNonNull(windowStart, "windowStart");
        Objects.requireNonNull(windowEnd, "windowEnd");
        Objects.requireNonNull(plannedOffice, "plannedOffice");
        if (!windowEnd.isAfter(windowStart)) {
            throw new IllegalArgumentException("trip window must end after it starts");
        }
        pings = List.copyOf(pings);
        milestones = List.copyOf(milestones);
        plannedStops = List.copyOf(plannedStops);
    }
}
