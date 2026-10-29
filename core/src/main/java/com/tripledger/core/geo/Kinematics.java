package com.tripledger.core.geo;

import com.tripledger.core.model.Ping;

/** How fast would the cab have to go to get from one ping to the other? */
public final class Kinematics {

    private static final double MS_PER_S_TO_KMPH = 3.6;

    private Kinematics() {
    }

    // km/h, straight line. Doesn't care which ping you pass first.
    public static double speedKmph(Ping a, Ping b) {
        long millis = Math.abs(b.timestamp().toEpochMilli() - a.timestamp().toEpochMilli());
        double metres = Haversine.metresBetween(a.point(), b.point());
        if (millis == 0) {
            // same instant: either it didn't move, or it "was in two places at once" -> infinitely fast
            return metres == 0 ? 0.0 : Double.POSITIVE_INFINITY;
        }
        double seconds = millis / 1000.0;
        return metres / seconds * MS_PER_S_TO_KMPH;
    }

    // exactly at the limit still counts as ok
    public static boolean isPlausible(Ping a, Ping b, double maxKmph) {
        return speedKmph(a, b) <= maxKmph;
    }
}
