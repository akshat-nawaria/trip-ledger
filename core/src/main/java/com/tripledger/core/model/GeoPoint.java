package com.tripledger.core.model;

/**
 * A lat/lon point, stored as micro-degrees (degrees x 1,000,000) in longs.
 *
 * Why not just doubles? Because I want two pings at the same spot to be *equal*,
 * not "equal-ish". With longs, equals/hashCode just work and dedupe stays exact.
 * 1 micro-degree is roughly 11 cm on the ground, which is more than enough here.
 *
 * Note: this accepts whatever the device sends, even garbage. Use isValid() to check.
 * Bad points get thrown out during cleaning, we don't want a request to crash over one bad fix.
 */
public record GeoPoint(long latE6, long lonE6) {

    private static final double MICRO = 1_000_000.0;
    private static final long MAX_LAT_E6 = 90_000_000L;
    private static final long MAX_LON_E6 = 180_000_000L;

    // rounds to the nearest micro-degree
    public static GeoPoint ofDegrees(double latitude, double longitude) {
        return new GeoPoint(StrictMath.round(latitude * MICRO), StrictMath.round(longitude * MICRO));
    }

    public double latitude() {
        return latE6 / MICRO;
    }

    public double longitude() {
        return lonE6 / MICRO;
    }

    // (0, 0) is what a lot of devices report when they have no fix at all, so treat it as junk
    public boolean isValid() {
        boolean inBounds = Math.abs(latE6) <= MAX_LAT_E6 && Math.abs(lonE6) <= MAX_LON_E6;
        boolean nullIsland = latE6 == 0 && lonE6 == 0;
        return inBounds && !nullIsland;
    }

    @Override
    public String toString() {
        return "(" + latitude() + ", " + longitude() + ")";
    }
}
