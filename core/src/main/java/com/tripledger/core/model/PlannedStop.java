package com.tripledger.core.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Where and when we *expected* to pick up or drop an employee.
 *
 * For an ADHOC rider there's no proper plan, so the trip system fills this with the
 * home address from their profile and the time they signed in. Same shape, different meaning -
 * the riderType tells the anchor resolver which one it's looking at.
 */
public record PlannedStop(String employeeRef, RiderType riderType, GeoPoint point, Instant time) {

    public PlannedStop {
        Objects.requireNonNull(employeeRef, "employeeRef");
        Objects.requireNonNull(riderType, "riderType");
    }
}
