package com.tripledger.core.geo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * A distance in whole millimetres. Never negative.
 *
 * I went with long millimetres instead of double km on purpose. Adding up doubles
 * gives slightly different answers depending on the order you add them in, and
 * across ~1500 segments that can flip a bill from 30.60 to 30.61 km. Not acceptable
 * for something we pay money on. Integers add up the same way every time.
 *
 * We only round to km once, right at the end (see toBillableKm).
 */
public record Distance(long millimetres) implements Comparable<Distance> {

    public static final Distance ZERO = new Distance(0);

    private static final int KM_DECIMALS = 2; // 0.01 km, i.e. 10 m

    public Distance {
        if (millimetres < 0) {
            throw new IllegalArgumentException("distance cannot be negative: " + millimetres + " mm");
        }
    }

    public static Distance ofMillimetres(long millimetres) {
        return new Distance(millimetres);
    }

    // metres -> nearest mm (half-even, so we don't always lean upwards)
    public static Distance ofMetres(double metres) {
        if (!Double.isFinite(metres) || metres < 0) {
            throw new IllegalArgumentException("metres must be finite and >= 0 but was " + metres);
        }
        return new Distance((long) StrictMath.rint(metres * 1000.0));
    }

    // order of the list doesn't matter here, that's the whole point
    public static Distance sum(Collection<Distance> distances) {
        long total = 0;
        for (Distance d : distances) {
            total = Math.addExact(total, d.millimetres);
        }
        return new Distance(total);
    }

    public Distance plus(Distance other) {
        return new Distance(Math.addExact(millimetres, other.millimetres));
    }

    public double metres() {
        return millimetres / 1000.0;
    }

    // the number that actually goes on the invoice: km, 2 decimals, HALF_EVEN, rounded once
    public BigDecimal toBillableKm() {
        return BigDecimal.valueOf(millimetres).movePointLeft(6).setScale(KM_DECIMALS, RoundingMode.HALF_EVEN);
    }

    @Override
    public int compareTo(Distance other) {
        return Long.compare(millimetres, other.millimetres);
    }

    @Override
    public String toString() {
        return millimetres + " mm";
    }
}
