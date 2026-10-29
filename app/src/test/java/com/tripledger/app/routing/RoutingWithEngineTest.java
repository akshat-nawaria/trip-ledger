package com.tripledger.app.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.tripledger.core.config.BillingRules;
import com.tripledger.core.engine.DistanceEngine;
import com.tripledger.core.geo.Distance;
import com.tripledger.core.geo.Haversine;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The real distance engine from core, plugged into the real routing chain from this module
 * (mock provider -> breaker -> cache -> gateway). No Spring, no network.
 *
 * The trip: leave the office, 9 km of good pings heading north, then a 22 min hole in the GPS
 * while the cab moves 2.4 km east, then 7 km more of good pings. Mock road = straight line x 1.3,
 * so the hole should be paid as about 3.12 km and the whole trip as about 19.12 km.
 */
class RoutingWithEngineTest {

    private static final Instant T0 = Instant.parse("2026-10-29T12:30:00Z");
    private static final GeoPoint OFFICE = GeoPoint.ofDegrees(12.9698, 77.7500);
    private static final double METRES_PER_DEGREE = Haversine.EARTH_RADIUS_METRES * Math.PI / 180.0;

    private final DistanceEngine engine = new DistanceEngine(BillingRules.defaults());
    private final MockRoutingProvider mock = new MockRoutingProvider(1.3);
    private final RoutingGateway gateway = TestRouting.gateway(mock, TestRouting.newBreaker());

    private final List<Ping> beforeGap = northFrom(OFFICE, 0, 181);     // t = 0..900 s, 9.0 km
    private final GeoPoint gapStart = beforeGap.get(180).point();
    private final GeoPoint gapEnd = east(gapStart, 2_400);
    private final List<Ping> afterGap = northFrom(gapEnd, 2_220, 141);  // t = 2220..2920 s, 7.0 km

    private TripEvidence trip() {
        List<Ping> pings = new ArrayList<>(beforeGap);
        pings.addAll(afterGap);
        return new TripEvidence("T-ROUTING", TripType.LOGOUT, T0, T0.plusSeconds(7_200), OFFICE, pings,
                List.of(new Milestone(null, MilestoneType.TRIP_START, OFFICE, T0)), List.of());
    }

    // what the gap should cost: the mock's road distance between the two snapped cell centres
    private Distance expectedGapDistance() {
        RouteKey key = RouteKey.snap(gapStart, gapEnd, TestRouting.CELL);
        return Distance.ofMetres(Haversine.metresBetween(key.from(), key.to()) * 1.3);
    }

    @Test
    void theGapIsRoutedThroughTheGatewayAndTheTripComesOutNormal() {
        TripRouting routing = gateway.newTrip();

        TripDistance result = engine.compute(trip(), routing);

        Segment gap = result.segments().stream().filter(s -> s.label() == SegmentLabel.ROUTED).findFirst().orElseThrow();
        assertThat(result.status()).isEqualTo(TripStatus.COMPUTED);
        assertThat(result.segmentsWith(SegmentLabel.ROUTED)).isEqualTo(1L);
        assertThat(gap.distance()).isEqualTo(expectedGapDistance());
        assertThat(result.billableKm().doubleValue()).isCloseTo(19.12, within(0.05));
        assertThat(routing.usage()).isEqualTo(new RoutingUsage(1, 0, 1, 0));
    }

    @Test
    void twentyTripsOverTheSameGapCostOneProviderCall() {
        TripDistance first = engine.compute(trip(), gateway.newTrip());
        int requests = first.routeRequests();

        for (int i = 1; i < 20; i++) {
            TripRouting routing = gateway.newTrip();
            TripDistance again = engine.compute(trip(), routing);

            assertThat(again.total()).isEqualTo(first.total());
            assertThat(routing.usage()).isEqualTo(new RoutingUsage(1, 1, 0, 0));
            requests += again.routeRequests();
        }

        assertThat(requests).isEqualTo(20);
        assertThat(mock.calls()).isEqualTo(1); // 20 lookups, 1 paid call
    }

    @Test
    void aProviderOutageGivesADegradedTripNotAnError() {
        mock.setDown(true);
        TripRouting routing = gateway.newTrip();

        TripDistance result = engine.compute(trip(), routing);

        assertThat(result.status()).isEqualTo(TripStatus.DEGRADED);
        assertThat(result.segmentsWith(SegmentLabel.DEGRADED_AERIAL)).isEqualTo(1L);
        assertThat(result.segmentsWith(SegmentLabel.ROUTED)).isEqualTo(0L);
        assertThat(routing.usage()).isEqualTo(new RoutingUsage(1, 0, 1, 1));
    }

    @Test
    void theSameTripComputesNormallyAgainOnceTheProviderIsBack() {
        mock.setDown(true);
        TripDistance duringOutage = engine.compute(trip(), gateway.newTrip());
        mock.setDown(false);
        TripDistance afterOutage = engine.compute(trip(), gateway.newTrip());

        // what a gateway that never saw an outage says about the same trip
        RoutingGateway neverDown = TestRouting.gateway(new MockRoutingProvider(1.3), TestRouting.newBreaker());
        TripDistance healthy = engine.compute(trip(), neverDown.newTrip());

        assertThat(duringOutage.status()).isEqualTo(TripStatus.DEGRADED);
        assertThat(afterOutage.status()).isEqualTo(TripStatus.COMPUTED);
        assertThat(afterOutage.total()).isEqualTo(healthy.total());
    }

    // ---- little track helpers (same idea as TestTrack in core, which isn't visible from here) ----

    private static List<Ping> northFrom(GeoPoint start, int startSecond, int count) {
        List<Ping> pings = new ArrayList<>(count);
        GeoPoint current = start;
        for (int i = 0; i < count; i++) {
            int second = startSecond + i * 5;
            pings.add(new Ping(T0.plusSeconds(second), current, 5, second));
            current = GeoPoint.ofDegrees(current.latitude() + 50 / METRES_PER_DEGREE, current.longitude());
        }
        return pings;
    }

    private static GeoPoint east(GeoPoint from, double metres) {
        double metresPerDegreeLon = METRES_PER_DEGREE * Math.cos(Math.toRadians(from.latitude()));
        return GeoPoint.ofDegrees(from.latitude(), from.longitude() + metres / metresPerDegreeLon);
    }
}
