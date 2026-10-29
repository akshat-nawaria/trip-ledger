package com.tripledger.core.geo;

import com.tripledger.core.model.GeoPoint;

/**
 * Straight-line (great-circle) distance between two points.
 *
 * Using StrictMath here, not Math. Math.sin/cos are allowed to use CPU tricks that can
 * differ in the last bit from machine to machine. Sounds tiny, but I need the exact same
 * metres on my laptop, in CI and on the server, otherwise "same input = same bill" breaks.
 */
public final class Haversine {

    // mean Earth radius. Treat this as part of the rules - change it and every bill changes.
    public static final double EARTH_RADIUS_METRES = 6_371_008.8;

    private Haversine() {
    }

    public static double metresBetween(GeoPoint from, GeoPoint to) {
        double phi1 = StrictMath.toRadians(from.latitude());
        double phi2 = StrictMath.toRadians(to.latitude());
        double deltaPhi = StrictMath.toRadians(to.latitude() - from.latitude());
        double deltaLambda = StrictMath.toRadians(to.longitude() - from.longitude());

        double sinHalfPhi = StrictMath.sin(deltaPhi / 2);
        double sinHalfLambda = StrictMath.sin(deltaLambda / 2);
        double h = sinHalfPhi * sinHalfPhi
                + StrictMath.cos(phi1) * StrictMath.cos(phi2) * sinHalfLambda * sinHalfLambda;

        // for points on opposite sides of the globe h can come out as 1.0000000002 and asin blows up (NaN)
        h = StrictMath.min(1.0, h);
        return 2 * EARTH_RADIUS_METRES * StrictMath.asin(StrictMath.sqrt(h));
    }

    public static Distance between(GeoPoint from, GeoPoint to) {
        return Distance.ofMetres(metresBetween(from, to));
    }
}
