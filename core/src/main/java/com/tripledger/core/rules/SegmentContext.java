package com.tripledger.core.rules;

import com.tripledger.core.config.BillingRules;
import com.tripledger.core.engine.RouteAnswer;
import com.tripledger.core.engine.RouteLookup;
import com.tripledger.core.geo.Distance;
import com.tripledger.core.geo.Haversine;
import com.tripledger.core.model.TrackPoint;
import java.time.Duration;

/**
 * Everything a rule needs to judge one segment, worked out once.
 *
 * The road distance is fetched lazily and at most once. Rules 0-2 never touch it, so short
 * gaps and parked time cost nothing. Rules 3-6 share the same answer.
 */
public final class SegmentContext {

    private static final double MS_PER_S_TO_KMPH = 3.6;

    private final TrackPoint from;
    private final TrackPoint to;
    private final BillingRules rules;
    private final RouteLookup routeLookup;
    private final double aerialMetres;
    private final Duration gap;

    private RouteAnswer routeAnswer; // null until a rule asks for it

    public SegmentContext(TrackPoint from, TrackPoint to, BillingRules rules, RouteLookup routeLookup) {
        this.from = from;
        this.to = to;
        this.rules = rules;
        this.routeLookup = routeLookup;
        this.aerialMetres = Haversine.metresBetween(from.point(), to.point());
        this.gap = Duration.between(from.time(), to.time()).abs();
    }

    public TrackPoint from() {
        return from;
    }

    public TrackPoint to() {
        return to;
    }

    public BillingRules rules() {
        return rules;
    }

    public Duration gap() {
        return gap;
    }

    public double aerialMetres() {
        return aerialMetres;
    }

    public Distance aerialDistance() {
        return Distance.ofMetres(aerialMetres);
    }

    /**
     * Straight-line speed in km/h.
     *
     * Zero-second gaps happen when an anchor lands on the exact second of a ping. If they're
     * within GPS accuracy of each other (50 m by default) that's just the same spot, call it 0.
     * Further apart at the same instant is physically impossible, so: infinite.
     */
    public double aerialSpeedKmph() {
        if (gap.isZero()) {
            return aerialMetres <= rules.maxAccuracyMetres() ? 0.0 : Double.POSITIVE_INFINITY;
        }
        return speedKmph(aerialMetres);
    }

    public double speedKmph(double metres) {
        if (gap.isZero()) {
            return metres == 0 ? 0.0 : Double.POSITIVE_INFINITY;
        }
        double seconds = gap.toMillis() / 1000.0;
        return metres / seconds * MS_PER_S_TO_KMPH;
    }

    public RouteAnswer route() {
        if (routeAnswer == null) {
            routeAnswer = routeLookup.route(from.point(), to.point());
        }
        return routeAnswer;
    }

    public boolean routeWasRequested() {
        return routeAnswer != null;
    }
}
