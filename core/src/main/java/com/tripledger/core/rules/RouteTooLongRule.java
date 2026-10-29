package com.tripledger.core.rules;

import com.tripledger.core.engine.RouteAnswer;
import com.tripledger.core.model.SegmentLabel;
import java.util.Optional;

/**
 * Rule 4. The router says this one gap is over 100 km. Inside a city commute that's
 * not a real route, it's the router snapping to the wrong road or a bad point.
 * Don't pay it - pay the straight line and flag it.
 */
public final class RouteTooLongRule implements SegmentRule {

    private static final double METRES_PER_KM = 1000.0;

    @Override
    public Optional<SegmentDecision> apply(SegmentContext segment) {
        if (!(segment.route() instanceof RouteAnswer.Found found)) {
            return Optional.empty();
        }
        double limitMetres = segment.rules().maxRouteKm() * METRES_PER_KM;
        if (found.drivingDistance().metres() <= limitMetres) {
            return Optional.empty();
        }
        return Optional.of(new SegmentDecision(SegmentLabel.REJECTED_DISTANCE, segment.aerialDistance()));
    }
}
