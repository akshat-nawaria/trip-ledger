package com.tripledger.core.rules;

import com.tripledger.core.engine.RouteAnswer;
import com.tripledger.core.model.SegmentLabel;
import java.util.Optional;

/**
 * Rule 5. Driving the router's route in the time we had would mean going over the limit,
 * AND even our own straight line x 1.4 estimate would be over the limit.
 *
 * Both have to be true (that's how the case study puts it). If only the router's number is
 * too fast, the router probably picked a long way round but the trip itself was fine, so we
 * let rule 6 pay it.
 */
public final class RouteTooFastRule implements SegmentRule {

    @Override
    public Optional<SegmentDecision> apply(SegmentContext segment) {
        if (!(segment.route() instanceof RouteAnswer.Found found)) {
            return Optional.empty();
        }
        double limit = segment.rules().maxSpeedKmph();
        double routedSpeed = segment.speedKmph(found.drivingDistance().metres());
        double estimatedSpeed = segment.speedKmph(segment.aerialMetres() * segment.rules().degradedRouteFactor());
        if (routedSpeed > limit && estimatedSpeed > limit) {
            return Optional.of(new SegmentDecision(SegmentLabel.REJECTED_SPEED, segment.aerialDistance()));
        }
        return Optional.empty();
    }
}
