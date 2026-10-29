package com.tripledger.core.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Something that happened on the trip - a pickup, a drop, a no-show, trip start/end.
 *
 * point and time are allowed to be null on purpose. In real life the driver app sometimes
 * sends a drop with no location, or a timestamp from a phone with a broken clock. The anchor
 * resolvers decide whether to trust it or fall back to the plan.
 *
 * employeeRef is an opaque token (no names/phones here). It's null for trip start/end.
 */
public record Milestone(String employeeRef, MilestoneType type, GeoPoint point, Instant time) {

    public Milestone {
        Objects.requireNonNull(type, "type");
    }
}
