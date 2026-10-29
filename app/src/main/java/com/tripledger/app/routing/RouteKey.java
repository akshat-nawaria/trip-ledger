package com.tripledger.app.routing;

import com.tripledger.core.model.GeoPoint;

/**
 * The cache key for one road-distance question: "from this grid cell to that grid cell".
 *
 * Two gaps that start and end a few metres apart are really the same question, so both ends get
 * snapped onto a grid and the cells become the key. The part that matters for billing: we then ask
 * the provider about the cell CENTRES, not the raw points. So the answer depends only on the key,
 * never on which trip happened to ask first. Same bill whatever order the trips run in.
 *
 * Direction matters (one-way roads, U-turns), so A -> B and B -> A are two different keys.
 */
public record RouteKey(long fromLatE6, long fromLonE6, long toLatE6, long toLonE6) {

    private static final long MAX_LAT_E6 = 90_000_000L;
    private static final long MAX_LON_E6 = 180_000_000L;

    public static RouteKey snap(GeoPoint from, GeoPoint to, long cellMicroDegrees) {
        if (cellMicroDegrees <= 0) {
            throw new IllegalArgumentException("cell size must be positive but was " + cellMicroDegrees);
        }
        return new RouteKey(
                centre(from.latE6(), cellMicroDegrees, MAX_LAT_E6),
                centre(from.lonE6(), cellMicroDegrees, MAX_LON_E6),
                centre(to.latE6(), cellMicroDegrees, MAX_LAT_E6),
                centre(to.lonE6(), cellMicroDegrees, MAX_LON_E6));
    }

    // the points we actually send to the provider
    public GeoPoint from() {
        return new GeoPoint(fromLatE6, fromLonE6);
    }

    public GeoPoint to() {
        return new GeoPoint(toLatE6, toLonE6);
    }

    // floorDiv and not plain "/": plain division rounds towards zero, which would make the cell
    // around 0 twice as wide as the others. Only matters near the equator / meridian, but still.
    // The clamp is there so a point right on the pole or the date line can't snap off the map.
    private static long centre(long valueE6, long cell, long limit) {
        long centre = Math.floorDiv(valueE6, cell) * cell + cell / 2;
        return Math.max(-limit, Math.min(limit, centre));
    }
}
