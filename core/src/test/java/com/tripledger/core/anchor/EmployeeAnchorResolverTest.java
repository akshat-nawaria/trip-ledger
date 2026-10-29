package com.tripledger.core.anchor;

import static com.tripledger.core.support.TestTrack.ORIGIN;
import static com.tripledger.core.support.TestTrack.east;
import static com.tripledger.core.support.TestTrack.north;
import static com.tripledger.core.support.TestTrips.adhoc;
import static com.tripledger.core.support.TestTrips.at;
import static com.tripledger.core.support.TestTrips.milestone;
import static com.tripledger.core.support.TestTrips.planned;
import static com.tripledger.core.support.TestTrips.trip;
import static org.assertj.core.api.Assertions.assertThat;

import com.tripledger.core.config.BillingRules;
import com.tripledger.core.model.Anchor;
import com.tripledger.core.model.AnchorKind;
import com.tripledger.core.model.AnchorSource;
import com.tripledger.core.model.GeoPoint;
import com.tripledger.core.model.Milestone;
import com.tripledger.core.model.MilestoneType;
import com.tripledger.core.model.TripType;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmployeeAnchorResolverTest {

    private static final GeoPoint ACTUAL_DROP = north(ORIGIN, 4_000);
    private static final GeoPoint PLANNED_HOME = north(ORIGIN, 4_050);
    private static final GeoPoint PROFILE_HOME = east(ORIGIN, 3_000);

    private final EmployeeAnchorResolver resolver = new EmployeeAnchorResolver();
    private final BillingRules rules = BillingRules.defaults();

    @Test
    void usesTheActualDropWhenItLooksFine() {
        var evidence = trip(TripType.LOGOUT, List.of(),
                List.of(milestone("e-1", MilestoneType.DROP, ACTUAL_DROP, 900)),
                List.of(planned("e-1", PLANNED_HOME, 960)));

        List<Anchor> anchors = resolver.resolve(evidence, rules);

        assertThat(anchors).containsExactly(
                new Anchor(AnchorKind.EMPLOYEE_DROP, "e-1", ACTUAL_DROP, at(900), AnchorSource.ACTUAL));
    }

    @Test
    void adhocRiderWithBrokenActualFallsBackToProfileHomeAndSignInTime() {
        var evidence = trip(TripType.LOGOUT, List.of(),
                List.of(milestone("e-2", MilestoneType.DROP, GeoPoint.ofDegrees(0, 0), 900)),
                List.of(adhoc("e-2", PROFILE_HOME, 870)));

        assertThat(resolver.resolve(evidence, rules)).containsExactly(
                new Anchor(AnchorKind.EMPLOYEE_DROP, "e-2", PROFILE_HOME, at(870), AnchorSource.PROFILE_HOME));
    }

    @Test
    void plannedRiderWithMissingActualPointFallsBackToThePlan() {
        var evidence = trip(TripType.LOGOUT, List.of(),
                List.of(new Milestone("e-3", MilestoneType.DROP, null, at(900))),
                List.of(planned("e-3", PLANNED_HOME, 960)));

        assertThat(resolver.resolve(evidence, rules)).containsExactly(
                new Anchor(AnchorKind.EMPLOYEE_DROP, "e-3", PLANNED_HOME, at(960), AnchorSource.PLANNED));
    }

    @Test
    void actualWithAWrongClockIsNotTrusted() {
        var evidence = trip(TripType.LOGOUT, List.of(),
                List.of(milestone("e-4", MilestoneType.DROP, ACTUAL_DROP, -86_400)), // yesterday
                List.of(planned("e-4", PLANNED_HOME, 960)));

        assertThat(resolver.resolve(evidence, rules)).containsExactly(
                new Anchor(AnchorKind.EMPLOYEE_DROP, "e-4", PLANNED_HOME, at(960), AnchorSource.PLANNED));
    }

    @Test
    void noUsableActualAndNoPlanMeansNoAnchor() {
        var evidence = trip(TripType.LOGOUT, List.of(),
                List.of(new Milestone("e-5", MilestoneType.DROP, null, null)), List.of());

        assertThat(resolver.resolve(evidence, rules)).isEmpty();
    }

    @Test
    void someoneOnTheRosterWithoutAnyMilestoneGetsNoDetour() {
        var evidence = trip(TripType.LOGOUT, List.of(), List.of(), List.of(planned("e-6", PLANNED_HOME, 960)));

        assertThat(resolver.resolve(evidence, rules)).isEmpty();
    }

    @Test
    void pickupsBecomePickupAnchors() {
        var evidence = trip(TripType.LOGIN, List.of(),
                List.of(milestone("e-7", MilestoneType.PICKUP, ACTUAL_DROP, 300)), List.of());

        assertThat(resolver.resolve(evidence, rules).get(0).kind()).isEqualTo(AnchorKind.EMPLOYEE_PICKUP);
    }

    @Test
    void resultDoesNotDependOnTheOrderMilestonesArrivedIn() {
        Milestone first = milestone("e-1", MilestoneType.DROP, ACTUAL_DROP, 900);
        Milestone second = milestone("e-2", MilestoneType.DROP, PLANNED_HOME, 1200);

        var oneWay = resolver.resolve(trip(TripType.LOGOUT, List.of(), List.of(first, second), List.of()), rules);
        var otherWay = resolver.resolve(trip(TripType.LOGOUT, List.of(), List.of(second, first), List.of()), rules);

        assertThat(oneWay).isEqualTo(otherWay);
    }
}
