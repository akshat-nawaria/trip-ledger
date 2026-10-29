package com.tripledger.app.persistence;

import com.tripledger.core.model.GeoPoint;
import com.tripledger.core.model.TripType;
import java.time.Instant;
import java.util.UUID;

// a row of the trip table. latestRunId is null until the trip has been computed once.
public record TripRow(
        String tripId,
        TripType tripType,
        Instant windowStart,
        Instant windowEnd,
        GeoPoint office,
        TripState status,
        UUID latestRunId,
        Instant updatedAt) {

    // the details every upload for this trip has to agree on
    public boolean sameDetailsAs(TripType type, Instant start, Instant end, GeoPoint plannedOffice) {
        return tripType == type && windowStart.equals(start) && windowEnd.equals(end) && office.equals(plannedOffice);
    }
}
