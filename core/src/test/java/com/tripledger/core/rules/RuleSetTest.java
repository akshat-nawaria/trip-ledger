package com.tripledger.core.rules;

import static com.tripledger.core.support.TestTrack.ORIGIN;
import static com.tripledger.core.support.TestTrack.T0;
import static com.tripledger.core.support.TestTrack.north;
import static org.assertj.core.api.Assertions.assertThat;

import com.tripledger.core.config.BillingRules;
import com.tripledger.core.geo.Distance;
import com.tripledger.core.geo.Haversine;
import com.tripledger.core.model.GeoPoint;
import com.tripledger.core.model.SegmentLabel;
import com.tripledger.core.model.TrackPoint;
import com.tripledger.core.support.FakeRouteLookup;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every rule of v1, poked right at its edges (69 vs 70 s, 4.9 vs 5.1 km/h, 100 km vs a hair over...).
 * Also checks the order: e.g. an impossible jump must be rejected even if the gap is short.
 */
class RuleSetTest {

    private final RuleSet ruleSet = RuleSet.v2();
    private final BillingRules rules = BillingRules.defaults();

    // a segment that starts at ORIGIN at T0 and ends `metres` north, `seconds` later
    private SegmentContext segment(int seconds, double metres, FakeRouteLookup routes) {
        TrackPoint from = new TrackPoint(T0, ORIGIN, null);
        TrackPoint to = new TrackPoint(T0.plusSeconds(seconds), north(ORIGIN, metres), null);
        return new SegmentContext(from, to, rules, routes);
    }

    private SegmentContext segmentWithRoad(int seconds, double metres, double roadMetres, FakeRouteLookup routes) {
        GeoPoint end = north(ORIGIN, metres);
        routes.answer(ORIGIN, end, Distance.ofMetres(roadMetres));
        return segment(seconds, metres, routes);
    }

    @Test
    void v1HasSevenRulesInTheDocumentedOrder() {
        List<String> names = ruleSet.rules().stream().map(r -> r.getClass().getSimpleName()).toList();

        assertThat(ruleSet.version()).isEqualTo("v2");
        assertThat(names).containsExactly("ImpossibleSpeedRule", "ShortGapRule", "ParkedRule",
                "RoutingUnavailableRule", "RouteTooLongRule", "RouteTooFastRule", "RoutedRule");
    }

    @Test
    void gapOf69SecondsIsAShortGapAndCostsNoRoutingCall() {
        FakeRouteLookup routes = FakeRouteLookup.working();

        SegmentDecision decision = ruleSet.classify(segment(69, 500, routes));

        assertThat(decision.label()).isEqualTo(SegmentLabel.SHORT_GAP_AERIAL);
        assertThat(routes.calls()).isEqualTo(0);
    }

    @Test
    void gapOf70SecondsIsNoLongerShortAndGetsRouted() {
        FakeRouteLookup routes = FakeRouteLookup.working();

        SegmentDecision decision = ruleSet.classify(segment(70, 500, routes));

        assertThat(decision.label()).isEqualTo(SegmentLabel.ROUTED);
        assertThat(routes.calls()).isEqualTo(1);
    }

    @Test
    void longGapBarelyMovingIsParked() {
        FakeRouteLookup routes = FakeRouteLookup.working();

        SegmentDecision decision = ruleSet.classify(segment(600, 800, routes)); // 4.8 km/h

        assertThat(decision.label()).isEqualTo(SegmentLabel.PARKED);
        assertThat(decision.distance()).isEqualTo(Distance.ofMetres(Haversine.metresBetween(ORIGIN, north(ORIGIN, 800))));
        assertThat(routes.calls()).isEqualTo(0);
    }

    @Test
    void longGapJustAboveParkedSpeedGetsRouted() {
        FakeRouteLookup routes = FakeRouteLookup.working();

        SegmentDecision decision = ruleSet.classify(segment(600, 850, routes)); // 5.1 km/h

        assertThat(decision.label()).isEqualTo(SegmentLabel.ROUTED);
    }

    @Test
    void impossibleStraightLineIsRejectedWithoutPayingForARoute() {
        FakeRouteLookup routes = FakeRouteLookup.working();

        SegmentDecision decision = ruleSet.classify(segment(120, 20_000, routes)); // 600 km/h

        assertThat(decision.label()).isEqualTo(SegmentLabel.REJECTED_SPEED);
        assertThat(decision.label().needsReview()).isTrue();
        assertThat(routes.calls()).isEqualTo(0);
    }

    @Test
    void impossibleJumpBeatsShortGap() {
        SegmentDecision decision = ruleSet.classify(segment(30, 5_000, FakeRouteLookup.working()));

        assertThat(decision.label()).isEqualTo(SegmentLabel.REJECTED_SPEED);
    }

    @Test
    void sameSecondWithinGpsAccuracyIsJustTheSameSpot() {
        SegmentDecision decision = ruleSet.classify(segment(0, 30, FakeRouteLookup.working()));

        assertThat(decision.label()).isEqualTo(SegmentLabel.SHORT_GAP_AERIAL);
    }

    @Test
    void aTinyMoveInATinyTimeIsGpsNoiseNotSpeeding() {
        // 40 m in 1 s would be 144 km/h, but 40 m is inside GPS accuracy (50 m): no proof it moved at all
        SegmentDecision decision = ruleSet.classify(segment(1, 40, FakeRouteLookup.working()));

        assertThat(decision.label()).isEqualTo(SegmentLabel.SHORT_GAP_AERIAL);
        assertThat(decision.label().needsReview()).isFalse();
    }

    @Test
    void aMoveBeyondGpsAccuracyInATinyTimeIsStillRejected() {
        SegmentDecision decision = ruleSet.classify(segment(1, 60, FakeRouteLookup.working())); // 216 km/h

        assertThat(decision.label()).isEqualTo(SegmentLabel.REJECTED_SPEED);
    }

    @Test
    void sameSecondButFarApartIsImpossible() {
        SegmentDecision decision = ruleSet.classify(segment(0, 500, FakeRouteLookup.working()));

        assertThat(decision.label()).isEqualTo(SegmentLabel.REJECTED_SPEED);
    }

    @Test
    void routingDownUsesStraightLineTimesOnePointFour() {
        SegmentDecision decision = ruleSet.classify(segment(600, 3_000, FakeRouteLookup.down()));
        double aerial = Haversine.metresBetween(ORIGIN, north(ORIGIN, 3_000));

        assertThat(decision.label()).isEqualTo(SegmentLabel.DEGRADED_AERIAL);
        assertThat(decision.distance()).isEqualTo(Distance.ofMetres(aerial * 1.4));
    }

    @Test
    void routeOfExactly100KmIsStillPaid() {
        SegmentDecision decision = ruleSet.classify(
                segmentWithRoad(7_200, 60_000, 100_000, FakeRouteLookup.working()));

        assertThat(decision.label()).isEqualTo(SegmentLabel.ROUTED);
        assertThat(decision.distance()).isEqualTo(Distance.ofMetres(100_000));
    }

    @Test
    void routeJustOver100KmIsRejectedAndPaidAsStraightLine() {
        SegmentDecision decision = ruleSet.classify(
                segmentWithRoad(7_200, 60_000, 100_001, FakeRouteLookup.working()));

        assertThat(decision.label()).isEqualTo(SegmentLabel.REJECTED_DISTANCE);
        assertThat(decision.distance()).isEqualTo(
                Distance.ofMetres(Haversine.metresBetween(ORIGIN, north(ORIGIN, 60_000))));
    }

    @Test
    void routeTooFastAndEstimateTooFastIsRejected() {
        // 9.5 km straight in 5 min = 114 km/h (fine), but x1.4 = 160 km/h and the road (12 km) = 144 km/h
        SegmentDecision decision = ruleSet.classify(
                segmentWithRoad(300, 9_500, 12_000, FakeRouteLookup.working()));

        assertThat(decision.label()).isEqualTo(SegmentLabel.REJECTED_SPEED);
    }

    @Test
    void routeTooFastButEstimateFineIsStillPaid() {
        // the router went the long way (15 km in 5 min = 180 km/h) but 2 km x 1.4 in 5 min is only ~34 km/h
        SegmentDecision decision = ruleSet.classify(
                segmentWithRoad(300, 2_000, 15_000, FakeRouteLookup.working()));

        assertThat(decision.label()).isEqualTo(SegmentLabel.ROUTED);
        assertThat(decision.distance()).isEqualTo(Distance.ofMetres(15_000));
    }

    @Test
    void normalGapPaysTheRoadDistance() {
        SegmentDecision decision = ruleSet.classify(
                segmentWithRoad(660, 4_500, 7_000, FakeRouteLookup.working()));

        assertThat(decision.label()).isEqualTo(SegmentLabel.ROUTED);
        assertThat(decision.distance()).isEqualTo(Distance.ofMetres(7_000));
    }
}
