package com.tripledger.core.anchor;

import com.tripledger.core.config.BillingRules;
import com.tripledger.core.model.GeoPoint;
import com.tripledger.core.model.Milestone;
import com.tripledger.core.model.PlannedStop;
import com.tripledger.core.model.TripEvidence;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// small shared helpers so all three resolvers judge "is this point usable?" the same way
final class AnchorChecks {

    // milestones can arrive in any order; sort them so the result never depends on that
    static final Comparator<Milestone> MILESTONE_ORDER = Comparator
            .comparing(Milestone::time, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Milestone::type)
            .thenComparing(Milestone::employeeRef, Comparator.nullsFirst(Comparator.naturalOrder()));

    private AnchorChecks() {
    }

    // usable = we have both a point and a time, the point is a real place,
    // and the time is inside the trip window (with the usual clock-drift slack)
    static boolean usable(GeoPoint point, Instant time, TripEvidence trip, BillingRules rules) {
        return point != null && point.isValid() && time != null && insideWindow(time, trip, rules);
    }

    static boolean insideWindow(Instant time, TripEvidence trip, BillingRules rules) {
        Instant earliest = trip.windowStart().minus(rules.clockSkewTolerance());
        Instant latest = trip.windowEnd().plus(rules.clockSkewTolerance());
        return !time.isBefore(earliest) && !time.isAfter(latest);
    }

    static Optional<PlannedStop> plannedStopFor(String employeeRef, List<PlannedStop> stops) {
        if (employeeRef == null) {
            return Optional.empty();
        }
        return stops.stream().filter(s -> employeeRef.equals(s.employeeRef())).findFirst();
    }
}
