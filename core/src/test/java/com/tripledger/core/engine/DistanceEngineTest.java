package com.tripledger.core.engine;

import static com.tripledger.core.support.TestTrack.east;
import static com.tripledger.core.support.TestTrack.north;
import static com.tripledger.core.support.TestTrack.pathMetres;
import static com.tripledger.core.support.TestTrack.ping;
import static com.tripledger.core.support.TestTrack.straightNorth;
import static com.tripledger.core.support.TestTrips.OFFICE;
import static com.tripledger.core.support.TestTrips.milestone;
import static com.tripledger.core.support.TestTrips.planned;
import static com.tripledger.core.support.TestTrips.trip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.tripledger.core.cleaning.TripWindowFilter;
import com.tripledger.core.config.BillingRules;
import com.tripledger.core.geo.Distance;
import com.tripledger.core.geo.Haversine;
import com.tripledger.core.model.AnchorKind;
import com.tripledger.core.model.AnchorSource;
import com.tripledger.core.model.GeoPoint;
import com.tripledger.core.model.Milestone;
import com.tripledger.core.model.MilestoneType;
import com.tripledger.core.model.Ping;
import com.tripledger.core.model.Segment;
import com.tripledger.core.model.SegmentLabel;
import com.tripledger.core.model.TripDistance;
import com.tripledger.core.model.TripEvidence;
import com.tripledger.core.model.TripStatus;
import com.tripledger.core.model.TripType;
import com.tripledger.core.support.FakeRouteLookup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests of the engine. The big one is the underpass trip from the case study:
 * the naive "join the dots" sum says 18.4 km, the real trip was ~31 km, we should land on ~30.6.
 */
class DistanceEngineTest {

    private final DistanceEngine engine = new DistanceEngine(BillingRules.defaults());

    // ---- the underpass trip -------------------------------------------------------------
    //
    //  office --(9.0 km of pings)--> G0 ===== 22 min, no GPS ===== G1 --(7.0 km of pings)--> home
    //                                  \                         /
    //                                   \-- drop of EMP-2417 at D --/
    //
    //  straight across G0 -> G1 is only 2.4 km (that's what the naive sum pays),
    //  but the cab actually drove G0 -> D (7.0 km by road) and D -> G1 (7.6 km by road).

    private final List<Ping> beforeGap = straightNorth(181, 5, 50);              // t = 0..900 s, 9.0 km
    private final GeoPoint g0 = beforeGap.get(180).point();
    private final GeoPoint g1 = east(g0, 2_400);                                 // gap end, 2.4 km from G0
    private final GeoPoint dropPoint = east(north(g0, 3_987.6), 2_085.4);        // ~4.5 km from G0, ~4.0 km from G1
    private final List<Ping> afterGap = continueNorth(g1, 2_220, 141);           // t = 2220..2920 s, 7.0 km

    private TripEvidence underpassTrip() {
        List<Ping> pings = new ArrayList<>(beforeGap);
        pings.addAll(afterGap);
        return trip(TripType.LOGOUT, pings,
                List.of(milestone(null, MilestoneType.TRIP_START, OFFICE, 0),
                        milestone("EMP-2417", MilestoneType.DROP, dropPoint, 1_560)),
                List.of(planned("EMP-2417", dropPoint, 1_560)));
    }

    private FakeRouteLookup underpassRoads() {
        return FakeRouteLookup.working()
                .answer(g0, dropPoint, Distance.ofMetres(7_000))
                .answer(dropPoint, g1, Distance.ofMetres(7_600));
    }

    @Test
    void underpassTripComesOutAtAbout30Point6KmNot18Point4() {
        List<Ping> allPings = new ArrayList<>(beforeGap);
        allPings.addAll(afterGap);
        double naiveKm = pathMetres(allPings) / 1000.0;

        TripDistance result = engine.compute(underpassTrip(), underpassRoads());

        assertThat(naiveKm).isCloseTo(18.4, within(0.05));
        assertThat(result.billableKm().doubleValue()).isCloseTo(30.6, within(0.05));
    }

    @Test
    void underpassTotalIsExactlyThePingsPlusTheTwoRoutedPieces() {
        Distance expected = Distance.ofMillimetres(
                aerialMillimetres(beforeGap) + 7_000_000 + 7_600_000 + aerialMillimetres(afterGap));

        TripDistance result = engine.compute(underpassTrip(), underpassRoads());

        assertThat(result.total()).isEqualTo(expected);
    }

    @Test
    void underpassNeedsOnlyTwoRoutingCalls() {
        FakeRouteLookup roads = underpassRoads();

        TripDistance result = engine.compute(underpassTrip(), roads);

        assertThat(roads.calls()).isEqualTo(2);
        assertThat(result.routeRequests()).isEqualTo(2);
        assertThat(result.segmentsWith(SegmentLabel.ROUTED)).isEqualTo(2L);
        assertThat(result.routesUsed()).hasSize(2);
        assertThat(result.status()).isEqualTo(TripStatus.COMPUTED);
    }

    @Test
    void underpassUsesTheOfficeAndTheDropAsAnchors() {
        TripDistance result = engine.compute(underpassTrip(), underpassRoads());

        assertThat(result.anchors()).hasSize(2);
        assertThat(result.anchors().get(0).kind()).isEqualTo(AnchorKind.OFFICE);
        assertThat(result.anchors().get(0).source()).isEqualTo(AnchorSource.ACTUAL);
        assertThat(result.anchors().get(1).kind()).isEqualTo(AnchorKind.EMPLOYEE_DROP);
        assertThat(result.anchors().get(1).employeeRef()).isEqualTo("EMP-2417");
    }

    @Test
    void everySegmentHasALabelAndTheyAreNumberedInOrder() {
        TripDistance result = engine.compute(underpassTrip(), underpassRoads());

        for (int i = 0; i < result.segments().size(); i++) {
            Segment s = result.segments().get(i);
            assertThat(s.seq()).isEqualTo(i + 1);
            assertThat(s.label() != null).isTrue();
            assertThat(s.from().time().isAfter(s.to().time())).isFalse();
        }
    }

    // ---- outages and bad routes -----------------------------------------------------------

    @Test
    void routingDownMarksTheTripDegradedButStillBills() {
        TripDistance result = engine.compute(underpassTrip(), FakeRouteLookup.down());

        double guessMetres = (Haversine.metresBetween(g0, dropPoint) + Haversine.metresBetween(dropPoint, g1)) * 1.4;
        double expectedKm = (aerialMillimetres(beforeGap) + aerialMillimetres(afterGap)) / 1e6 + guessMetres / 1000;

        assertThat(result.status()).isEqualTo(TripStatus.DEGRADED);
        assertThat(result.segmentsWith(SegmentLabel.DEGRADED_AERIAL)).isEqualTo(2L);
        assertThat(result.routesUsed()).isEmpty();
        assertThat(result.total().metres() / 1000).isCloseTo(expectedKm, within(0.001));
    }

    @Test
    void crazyRouteAnswerIsRejectedAndFlagged() {
        FakeRouteLookup roads = FakeRouteLookup.working()
                .answer(g0, dropPoint, Distance.ofMetres(150_000))   // router lost its mind
                .answer(dropPoint, g1, Distance.ofMetres(7_600));

        TripDistance result = engine.compute(underpassTrip(), roads);

        assertThat(result.segmentsWith(SegmentLabel.REJECTED_DISTANCE)).isEqualTo(1L);
        assertThat(result.segments().stream().filter(s -> s.label().needsReview()).count()).isEqualTo(1L);
        assertThat(result.total().metres()).isLessThan(30_000);
    }

    // ---- cheap trips and messy input ------------------------------------------------------

    @Test
    void tripWithOnlyShortGapsNeverCallsTheRouter() {
        FakeRouteLookup roads = FakeRouteLookup.working();
        TripEvidence evidence = trip(TripType.LOGOUT, straightNorth(100, 5, 50),
                List.of(milestone(null, MilestoneType.TRIP_START, OFFICE, 0)), List.of());

        TripDistance result = engine.compute(evidence, roads);

        assertThat(roads.calls()).isEqualTo(0);
        assertThat(result.segmentsWith(SegmentLabel.SHORT_GAP_AERIAL)).isEqualTo((long) result.segments().size());
    }

    @Test
    void waitingTenMinutesAtAPickupIsParkedNotRouted() {
        List<Ping> pings = new ArrayList<>(straightNorth(10, 5, 50));             // t = 0..45
        GeoPoint gate = pings.get(9).point();
        pings.add(ping(645, north(gate, 15)));                                     // 10 min later, 15 m away
        pings.addAll(continueNorth(north(gate, 15), 650, 5));
        FakeRouteLookup roads = FakeRouteLookup.working();
        TripEvidence evidence = trip(TripType.LOGOUT, pings,
                List.of(milestone(null, MilestoneType.TRIP_START, OFFICE, 0)), List.of());

        TripDistance result = engine.compute(evidence, roads);

        assertThat(result.segmentsWith(SegmentLabel.PARKED)).isEqualTo(1L);
        assertThat(roads.calls()).isEqualTo(0);
    }

    @Test
    void caseStudyTeleportBurstDoesNotChangeTheTotal() {
        List<Ping> clean = straightNorth(40, 5, 50);
        List<Ping> withBurst = new ArrayList<>(clean);
        withBurst.add(ping(42, east(clean.get(8).point(), 9_000)));
        withBurst.add(ping(44, east(clean.get(8).point(), 9_050)));
        List<Milestone> start = List.of(milestone(null, MilestoneType.TRIP_START, OFFICE, 0));

        TripDistance withoutGlitch = engine.compute(trip(TripType.LOGOUT, clean, start, List.of()),
                FakeRouteLookup.working());
        TripDistance withGlitch = engine.compute(trip(TripType.LOGOUT, withBurst, start, List.of()),
                FakeRouteLookup.working());

        assertThat(withGlitch.total()).isEqualTo(withoutGlitch.total());
    }

    @Test
    void pingFromYesterdayIsIgnored() {
        List<Ping> pings = new ArrayList<>(straightNorth(20, 5, 50));
        pings.add(ping(-86_400, east(OFFICE, 30_000)));
        TripEvidence evidence = trip(TripType.LOGOUT, pings,
                List.of(milestone(null, MilestoneType.TRIP_START, OFFICE, 0)), List.of());

        TripDistance result = engine.compute(evidence, FakeRouteLookup.working());

        assertThat(result.cleaning().removedBy(TripWindowFilter.NAME)).isEqualTo(1);
    }

    @Test
    void noPingsAtAllGivesZeroKm() {
        TripEvidence evidence = trip(TripType.LOGIN, List.of(), List.of(), List.of());

        TripDistance result = engine.compute(evidence, FakeRouteLookup.working());

        assertThat(result.total()).isEqualTo(Distance.ZERO);
        assertThat(result.segments()).isEmpty();
    }

    // ---- determinism ------------------------------------------------------------------------

    @Test
    void shufflingTheInputNeverChangesTheResult() {
        TripEvidence original = underpassTrip();
        TripDistance expected = engine.compute(original, underpassRoads());

        Random random = new Random(2026);
        for (int round = 0; round < 100; round++) {
            List<Ping> pings = new ArrayList<>(original.pings());
            List<Milestone> milestones = new ArrayList<>(original.milestones());
            Collections.shuffle(pings, random);
            Collections.shuffle(milestones, random);
            TripEvidence shuffled = trip(TripType.LOGOUT, pings, milestones, original.plannedStops());

            assertThat(engine.compute(shuffled, underpassRoads())).isEqualTo(expected);
        }
    }

    @Test
    void resultCarriesTheRuleSetVersion() {
        assertThat(engine.compute(underpassTrip(), underpassRoads()).ruleSetVersion()).isEqualTo("v2");
    }

    // ---- helpers -----------------------------------------------------------------------------

    // pings heading north from `start`, one every 5 s, 50 m apart, beginning at `startSecond`
    private static List<Ping> continueNorth(GeoPoint start, int startSecond, int count) {
        List<Ping> pings = new ArrayList<>();
        GeoPoint position = start;
        for (int i = 0; i < count; i++) {
            pings.add(ping(startSecond + i * 5, position));
            position = north(position, 50);
        }
        return pings;
    }

    // the engine pays every short hop as its straight line, rounded to mm - same here
    private static long aerialMillimetres(List<Ping> pings) {
        long total = 0;
        for (int i = 1; i < pings.size(); i++) {
            total += Haversine.between(pings.get(i - 1).point(), pings.get(i).point()).millimetres();
        }
        return total;
    }
}
