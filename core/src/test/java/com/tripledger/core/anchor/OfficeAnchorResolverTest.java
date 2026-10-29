package com.tripledger.core.anchor;

import static com.tripledger.core.support.TestTrack.east;
import static com.tripledger.core.support.TestTrips.OFFICE;
import static com.tripledger.core.support.TestTrips.WINDOW_END;
import static com.tripledger.core.support.TestTrips.WINDOW_START;
import static com.tripledger.core.support.TestTrips.at;
import static com.tripledger.core.support.TestTrips.milestone;
import static com.tripledger.core.support.TestTrips.withMilestones;
import static org.assertj.core.api.Assertions.assertThat;

import com.tripledger.core.config.BillingRules;
import com.tripledger.core.model.Anchor;
import com.tripledger.core.model.AnchorKind;
import com.tripledger.core.model.AnchorSource;
import com.tripledger.core.model.GeoPoint;
import com.tripledger.core.model.MilestoneType;
import com.tripledger.core.model.TripType;
import org.junit.jupiter.api.Test;

class OfficeAnchorResolverTest {

    private final OfficeAnchorResolver resolver = new OfficeAnchorResolver();
    private final BillingRules rules = BillingRules.defaults(); // 400 m radius

    @Test
    void tripEndJustInsideTheRadiusIsTrusted() {
        GeoPoint nearGate = east(OFFICE, 399);
        var evidence = withMilestones(TripType.LOGIN, milestone(null, MilestoneType.TRIP_END, nearGate, 3000));

        assertThat(resolver.resolve(evidence, rules)).containsExactly(
                new Anchor(AnchorKind.OFFICE, null, nearGate, at(3000), AnchorSource.ACTUAL));
    }

    @Test
    void tripEndJustOutsideTheRadiusIsReplacedByTheOffice() {
        GeoPoint fewStreetsAway = east(OFFICE, 401);
        var evidence = withMilestones(TripType.LOGIN, milestone(null, MilestoneType.TRIP_END, fewStreetsAway, 3000));

        assertThat(resolver.resolve(evidence, rules)).containsExactly(
                new Anchor(AnchorKind.OFFICE, null, OFFICE, at(3000), AnchorSource.PLANNED_OFFICE));
    }

    @Test
    void logoutTripAnchorsTheOfficeAtTheStart() {
        GeoPoint gate = east(OFFICE, 50);
        var evidence = withMilestones(TripType.LOGOUT,
                milestone(null, MilestoneType.TRIP_START, gate, 60),
                milestone(null, MilestoneType.TRIP_END, east(OFFICE, 9_000), 3000));

        assertThat(resolver.resolve(evidence, rules)).containsExactly(
                new Anchor(AnchorKind.OFFICE, null, gate, at(60), AnchorSource.ACTUAL));
    }

    @Test
    void noTripEndAtAllUsesTheOfficeAtTheEndOfTheWindow() {
        var evidence = withMilestones(TripType.LOGIN);

        assertThat(resolver.resolve(evidence, rules)).containsExactly(
                new Anchor(AnchorKind.OFFICE, null, OFFICE, WINDOW_END, AnchorSource.PLANNED_OFFICE));
    }

    @Test
    void noTripStartOnALogoutUsesTheStartOfTheWindow() {
        var evidence = withMilestones(TripType.LOGOUT);

        assertThat(resolver.resolve(evidence, rules)).containsExactly(
                new Anchor(AnchorKind.OFFICE, null, OFFICE, WINDOW_START, AnchorSource.PLANNED_OFFICE));
    }

    @Test
    void tripEndWithAWrongClockFallsBackToTheWindowEnd() {
        var evidence = withMilestones(TripType.LOGIN, milestone(null, MilestoneType.TRIP_END, OFFICE, 999_999));

        assertThat(resolver.resolve(evidence, rules)).containsExactly(
                new Anchor(AnchorKind.OFFICE, null, OFFICE, WINDOW_END, AnchorSource.PLANNED_OFFICE));
    }

    @Test
    void ifTripEndWasSentTwiceTheLatestOneWins() {
        GeoPoint early = east(OFFICE, 100);
        GeoPoint late = east(OFFICE, 20);
        var evidence = withMilestones(TripType.LOGIN,
                milestone(null, MilestoneType.TRIP_END, late, 3100),
                milestone(null, MilestoneType.TRIP_END, early, 3000));

        assertThat(resolver.resolve(evidence, rules).get(0).point()).isEqualTo(late);
    }
}
