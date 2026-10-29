package com.tripledger.core.config;

import java.time.Duration;
import java.util.Objects;

/**
 * All the numbers the engine depends on, kept in one place so nothing is hard-coded
 * somewhere deep inside a filter.
 *
 * Defaults come straight from the case study. A client company can override them, and
 * since we save the rules used with every result, we can always explain an old bill later.
 *
 * Quick guide to the fields:
 *  - maxAccuracyMetres:   device says it's off by more than this -> drop the ping
 *  - maxSpeedKmph:        faster than this isn't a cab, it's a GPS glitch
 *  - shortGap:            gaps under this are just measured as a straight line
 *  - parkedSpeedKmph:     long gap but slower than this -> the cab was basically standing still
 *  - officeRadiusMetres:  only trust the real trip end if it's this close to the office
 *  - maxRouteKm:          a routed piece longer than this is clearly wrong
 *  - degradedRouteFactor: when the map service is down, straight line x this is our best guess
 *  - teleportReanchorRun: this many "bad" pings that agree with each other = maybe they're the real ones
 *  - clockSkewTolerance:  ignore pings this far outside the trip window (phone clocks lie)
 *  - noShowAnchorEnabled: some clients pay for the drive to a no-show's house, some don't
 */
public record BillingRules(
        int maxAccuracyMetres,
        double maxSpeedKmph,
        Duration shortGap,
        double parkedSpeedKmph,
        int officeRadiusMetres,
        int maxRouteKm,
        double degradedRouteFactor,
        int teleportReanchorRun,
        Duration clockSkewTolerance,
        boolean noShowAnchorEnabled) {

    public BillingRules {
        requirePositive(maxAccuracyMetres, "maxAccuracyMetres");
        requirePositive(maxSpeedKmph, "maxSpeedKmph");
        requirePositive(Objects.requireNonNull(shortGap, "shortGap").toMillis(), "shortGap");
        requirePositive(parkedSpeedKmph, "parkedSpeedKmph");
        requirePositive(officeRadiusMetres, "officeRadiusMetres");
        requirePositive(maxRouteKm, "maxRouteKm");
        if (degradedRouteFactor < 1.0) {
            throw new IllegalArgumentException("degradedRouteFactor must be >= 1.0 but was " + degradedRouteFactor);
        }
        if (teleportReanchorRun < 2) {
            throw new IllegalArgumentException("teleportReanchorRun must be >= 2 but was " + teleportReanchorRun);
        }
        Objects.requireNonNull(clockSkewTolerance, "clockSkewTolerance");
        if (clockSkewTolerance.isNegative()) {
            throw new IllegalArgumentException("clockSkewTolerance cannot be negative");
        }
        // would make no sense: "parked" can't be faster than "impossibly fast"
        if (parkedSpeedKmph >= maxSpeedKmph) {
            throw new IllegalArgumentException("parkedSpeedKmph must be below maxSpeedKmph");
        }
    }

    // the values from the case study PDF
    public static BillingRules defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    // handy when you want "same rules, but change one thing"
    public Builder toBuilder() {
        return new Builder()
                .maxAccuracyMetres(maxAccuracyMetres)
                .maxSpeedKmph(maxSpeedKmph)
                .shortGap(shortGap)
                .parkedSpeedKmph(parkedSpeedKmph)
                .officeRadiusMetres(officeRadiusMetres)
                .maxRouteKm(maxRouteKm)
                .degradedRouteFactor(degradedRouteFactor)
                .teleportReanchorRun(teleportReanchorRun)
                .clockSkewTolerance(clockSkewTolerance)
                .noShowAnchorEnabled(noShowAnchorEnabled);
    }

    private static void requirePositive(double value, String name) {
        if (!(value > 0)) {
            throw new IllegalArgumentException(name + " must be > 0 but was " + value);
        }
    }

    // starts with the case-study defaults, so you only set what you want to change
    public static final class Builder {
        private int maxAccuracyMetres = 50;
        private double maxSpeedKmph = 120.0;
        private Duration shortGap = Duration.ofSeconds(70);
        private double parkedSpeedKmph = 5.0;
        private int officeRadiusMetres = 400;
        private int maxRouteKm = 100;
        private double degradedRouteFactor = 1.4;
        private int teleportReanchorRun = 3;
        private Duration clockSkewTolerance = Duration.ofMinutes(10);
        private boolean noShowAnchorEnabled = false;

        private Builder() {
        }

        public Builder maxAccuracyMetres(int value) {
            this.maxAccuracyMetres = value;
            return this;
        }

        public Builder maxSpeedKmph(double value) {
            this.maxSpeedKmph = value;
            return this;
        }

        public Builder shortGap(Duration value) {
            this.shortGap = value;
            return this;
        }

        public Builder parkedSpeedKmph(double value) {
            this.parkedSpeedKmph = value;
            return this;
        }

        public Builder officeRadiusMetres(int value) {
            this.officeRadiusMetres = value;
            return this;
        }

        public Builder maxRouteKm(int value) {
            this.maxRouteKm = value;
            return this;
        }

        public Builder degradedRouteFactor(double value) {
            this.degradedRouteFactor = value;
            return this;
        }

        public Builder teleportReanchorRun(int value) {
            this.teleportReanchorRun = value;
            return this;
        }

        public Builder clockSkewTolerance(Duration value) {
            this.clockSkewTolerance = value;
            return this;
        }

        public Builder noShowAnchorEnabled(boolean value) {
            this.noShowAnchorEnabled = value;
            return this;
        }

        public BillingRules build() {
            return new BillingRules(maxAccuracyMetres, maxSpeedKmph, shortGap, parkedSpeedKmph,
                    officeRadiusMetres, maxRouteKm, degradedRouteFactor, teleportReanchorRun,
                    clockSkewTolerance, noShowAnchorEnabled);
        }
    }
}
