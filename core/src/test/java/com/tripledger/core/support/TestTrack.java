package com.tripledger.core.support;

import com.tripledger.core.geo.Haversine;
import com.tripledger.core.model.GeoPoint;
import com.tripledger.core.model.Ping;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// little helpers to build fake-but-realistic GPS tracks for the tests
public final class TestTrack {

    // fixed time so tests never depend on "now"
    public static final Instant T0 = Instant.parse("2026-10-29T12:30:00Z");

    // some office in Bengaluru, doesn't really matter where
    public static final GeoPoint ORIGIN = GeoPoint.ofDegrees(12.9698, 77.7500);

    public static final int GOOD_ACCURACY = 5;

    private static final double METRES_PER_DEGREE = Haversine.EARTH_RADIUS_METRES * Math.PI / 180.0;

    private TestTrack() {
    }

    public static GeoPoint north(GeoPoint from, double metres) {
        return GeoPoint.ofDegrees(from.latitude() + metres / METRES_PER_DEGREE, from.longitude());
    }

    public static GeoPoint east(GeoPoint from, double metres) {
        double metresPerDegreeLon = METRES_PER_DEGREE * Math.cos(Math.toRadians(from.latitude()));
        return GeoPoint.ofDegrees(from.latitude(), from.longitude() + metres / metresPerDegreeLon);
    }

    public static Ping ping(int secondsFromStart, GeoPoint point) {
        return ping(secondsFromStart, point, GOOD_ACCURACY);
    }

    public static Ping ping(int secondsFromStart, GeoPoint point, int accuracyMetres) {
        return new Ping(T0.plusSeconds(secondsFromStart), point, accuracyMetres, secondsFromStart);
    }

    // a cab driving straight north: `count` pings, one every `stepSeconds`, `metresPerStep` apart
    public static List<Ping> straightNorth(int count, int stepSeconds, double metresPerStep) {
        List<Ping> pings = new ArrayList<>(count);
        GeoPoint current = ORIGIN;
        for (int i = 0; i < count; i++) {
            pings.add(ping(i * stepSeconds, current));
            current = north(current, metresPerStep);
        }
        return pings;
    }

    // total straight-line length of the track, in metres
    public static double pathMetres(List<Ping> pings) {
        double total = 0;
        for (int i = 1; i < pings.size(); i++) {
            total += Haversine.metresBetween(pings.get(i - 1).point(), pings.get(i).point());
        }
        return total;
    }
}
