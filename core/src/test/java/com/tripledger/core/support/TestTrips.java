package com.tripledger.core.support;

import static com.tripledger.core.support.TestTrack.ORIGIN;
import static com.tripledger.core.support.TestTrack.T0;

import com.tripledger.core.model.GeoPoint;
import com.tripledger.core.model.Milestone;
import com.tripledger.core.model.MilestoneType;
import com.tripledger.core.model.Ping;
import com.tripledger.core.model.PlannedStop;
import com.tripledger.core.model.RiderType;
import com.tripledger.core.model.TripEvidence;
import com.tripledger.core.model.TripType;
import java.time.Instant;
import java.util.List;

// shortcuts for building trips in tests. The office is always at ORIGIN, the window is T0 .. T0 + 2h.
public final class TestTrips {

    public static final Instant WINDOW_START = T0;
    public static final Instant WINDOW_END = T0.plusSeconds(2 * 3600);
    public static final GeoPoint OFFICE = ORIGIN;

    private TestTrips() {
    }

    public static TripEvidence trip(TripType type, List<Ping> pings, List<Milestone> milestones,
            List<PlannedStop> stops) {
        return new TripEvidence("T-TEST", type, WINDOW_START, WINDOW_END, OFFICE, pings, milestones, stops);
    }

    public static TripEvidence withMilestones(TripType type, Milestone... milestones) {
        return trip(type, List.of(), List.of(milestones), List.of());
    }

    public static Instant at(int secondsFromStart) {
        return T0.plusSeconds(secondsFromStart);
    }

    public static Milestone milestone(String employee, MilestoneType type, GeoPoint point, int second) {
        return new Milestone(employee, type, point, at(second));
    }

    public static PlannedStop planned(String employee, GeoPoint point, int second) {
        return new PlannedStop(employee, RiderType.PLANNED, point, at(second));
    }

    public static PlannedStop adhoc(String employee, GeoPoint profileHome, int signInSecond) {
        return new PlannedStop(employee, RiderType.ADHOC, profileHome, at(signInSecond));
    }
}
