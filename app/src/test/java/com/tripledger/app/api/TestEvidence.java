package com.tripledger.app.api;

import com.tripledger.app.api.dto.EvidenceRequest;
import com.tripledger.core.geo.Haversine;
import com.tripledger.core.model.MilestoneType;
import com.tripledger.core.model.TripType;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds upload bodies for the API tests. Always the same shape of trip:
 *
 *   office -> 9 km north (181 pings, every 5 s) -> 22 min with no GPS while the cab moves 2.4 km east
 *          -> 7 km north (141 pings)
 *
 * With the mock road (straight line x 1.3) that's about 9 + 3.12 + 7 = 19.12 km.
 *
 * Every trip() call shifts the whole trip a few km east, so no two tests share a cached route
 * by accident (the route cache lives as long as the Spring context, across test classes).
 */
final class TestEvidence {

    static final OffsetDateTime T0 = OffsetDateTime.parse("2026-10-29T12:30:00Z");
    static final double EXPECTED_KM = 19.12;
    static final int PINGS = 322;

    private static final double METRES_PER_DEGREE = Haversine.EARTH_RADIUS_METRES * Math.PI / 180.0;
    private static final AtomicInteger NEXT_SHIFT = new AtomicInteger(1);

    private final double originLat = 12.9698;
    private final double originLon;
    private final List<EvidenceRequest.PingIn> beforeGap = new ArrayList<>();
    private final List<EvidenceRequest.PingIn> afterGap = new ArrayList<>();

    private TestEvidence() {
        double shiftMetres = 5_000.0 * NEXT_SHIFT.getAndIncrement();
        this.originLon = 77.7500 + shiftMetres / metresPerDegreeLon(originLat);

        double lat = originLat;
        for (int i = 0; i < 181; i++) {
            beforeGap.add(ping(i * 5, lat, originLon, i));
            lat += 50 / METRES_PER_DEGREE;
        }
        double gapEndLat = lat - 50 / METRES_PER_DEGREE; // same latitude as the last ping before the gap
        double gapEndLon = originLon + 2_400 / metresPerDegreeLon(gapEndLat);
        lat = gapEndLat;
        for (int i = 0; i < 141; i++) {
            afterGap.add(ping(2_220 + i * 5, lat, gapEndLon, 181 + i));
            lat += 50 / METRES_PER_DEGREE;
        }
    }

    static TestEvidence trip() {
        return new TestEvidence();
    }

    static String newTripId() {
        return "IT-" + UUID.randomUUID();
    }

    static String newKey() {
        return UUID.randomUUID().toString();
    }

    // the whole trip in one upload
    EvidenceRequest all() {
        List<EvidenceRequest.PingIn> pings = new ArrayList<>(beforeGap);
        pings.addAll(afterGap);
        return request(pings);
    }

    // the same trip split in two uploads, like a device that syncs every few minutes
    EvidenceRequest firstHalf() {
        return request(beforeGap);
    }

    EvidenceRequest secondHalf() {
        return request(afterGap);
    }

    // the whole trip, plus a drop of one employee at the 100th ping (so that employee "rode" in it)
    EvidenceRequest withDropOf(String employeeRef) {
        EvidenceRequest base = all();
        EvidenceRequest.PingIn at = beforeGap.get(100);
        List<EvidenceRequest.MilestoneIn> milestones = new ArrayList<>(base.milestones());
        milestones.add(new EvidenceRequest.MilestoneIn(employeeRef, MilestoneType.DROP, at.lat(), at.lon(), at.ts()));
        return new EvidenceRequest(base.tripType(), base.windowStart(), base.windowEnd(), base.plannedOffice(),
                base.pings(), milestones, base.plannedStops());
    }

    // latitude of the drop point above, as it would appear in JSON - the log test looks for it
    String dropLatitude() {
        return String.valueOf(beforeGap.get(100).lat());
    }

    // same trip, but the window ends an hour later - must clash with an earlier upload
    EvidenceRequest withDifferentWindow() {
        EvidenceRequest base = all();
        return new EvidenceRequest(base.tripType(), base.windowStart(), base.windowEnd().plusHours(1),
                base.plannedOffice(), base.pings(), base.milestones(), base.plannedStops());
    }

    private EvidenceRequest request(List<EvidenceRequest.PingIn> pings) {
        return new EvidenceRequest(
                TripType.LOGOUT,
                T0,
                T0.plusHours(2),
                new EvidenceRequest.Point(originLat, originLon),
                List.copyOf(pings),
                List.of(new EvidenceRequest.MilestoneIn(null, MilestoneType.TRIP_START, originLat, originLon, T0)),
                List.of());
    }

    private static EvidenceRequest.PingIn ping(int second, double lat, double lon, long seq) {
        return new EvidenceRequest.PingIn(T0.plusSeconds(second), lat, lon, 5, seq);
    }

    private static double metresPerDegreeLon(double lat) {
        return METRES_PER_DEGREE * Math.cos(Math.toRadians(lat));
    }
}
