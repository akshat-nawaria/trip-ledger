package com.tripledger.core.anchor;

import static com.tripledger.core.support.TestTrack.ORIGIN;
import static com.tripledger.core.support.TestTrack.north;
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
import com.tripledger.core.model.MilestoneType;
import com.tripledger.core.model.TripType;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoShowAnchorResolverTest {

    private static final GeoPoint DOOR = north(ORIGIN, 2_000);
    private static final GeoPoint PLANNED_DOOR = north(ORIGIN, 2_020);

    private final NoShowAnchorResolver resolver = new NoShowAnchorResolver();
    private final BillingRules flagOn = BillingRules.builder().noShowAnchorEnabled(true).build();

    @Test
    void flagOffMeansNoShowsAreIgnored() {
        var evidence = trip(TripType.LOGIN, List.of(),
                List.of(milestone("e-1", MilestoneType.NO_SHOW, DOOR, 600)), List.of());

        assertThat(resolver.resolve(evidence, BillingRules.defaults())).isEmpty();
    }

    @Test
    void flagOnUsesWhereTheDriverActuallyWaited() {
        var evidence = trip(TripType.LOGIN, List.of(),
                List.of(milestone("e-1", MilestoneType.NO_SHOW, DOOR, 600)), List.of());

        assertThat(resolver.resolve(evidence, flagOn)).containsExactly(
                new Anchor(AnchorKind.NO_SHOW, "e-1", DOOR, at(600), AnchorSource.ACTUAL));
    }

    @Test
    void flagOnWithBrokenActualFallsBackToThePlannedLocation() {
        var evidence = trip(TripType.LOGIN, List.of(),
                List.of(milestone("e-1", MilestoneType.NO_SHOW, GeoPoint.ofDegrees(0, 0), 600)),
                List.of(planned("e-1", PLANNED_DOOR, 590)));

        assertThat(resolver.resolve(evidence, flagOn)).containsExactly(
                new Anchor(AnchorKind.NO_SHOW, "e-1", PLANNED_DOOR, at(590), AnchorSource.PLANNED));
    }

    @Test
    void flagOnButNothingUsableMeansNoAnchor() {
        var evidence = trip(TripType.LOGIN, List.of(),
                List.of(milestone("e-1", MilestoneType.NO_SHOW, GeoPoint.ofDegrees(0, 0), 600)), List.of());

        assertThat(resolver.resolve(evidence, flagOn)).isEmpty();
    }
}
