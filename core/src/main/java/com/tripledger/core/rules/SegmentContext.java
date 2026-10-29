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
     * Two points no further apart than GPS accuracy (50 m by default) prove nothing about
     * movement - the phone's own error is that big. So that counts as standing still (0), however
     * short the time between them. Typical case: the driver app logs a drop 0.1 s after a ping and
     * 20 m away. Divide 20 m by 0.1 s and you get 720 km/h, which used to flag a perfectly normal
     * drop for review (rule set v1). The benchmark showed that happening ~0.6 times per trip.
     *
     * Further apart than GPS accuracy at the very same instant is physically impossible: infinite.
     */
    public double aerialSpeedKmph() {
        if (aerialMetres <= rules.maxAccuracyMetres()) {
            return 0.0;
        }
        if (gap.isZero()) {
            return Double.POSITIVE_INFINITY;
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
