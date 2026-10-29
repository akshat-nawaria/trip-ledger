package com.tripledger.core.rules;

import com.tripledger.core.engine.RouteAnswer;
import com.tripledger.core.geo.Distance;
import com.tripledger.core.model.SegmentLabel;
import java.util.Optional;

/**
 * Rule 3. This is a real gap and we need the road distance, but routing isn't answering.
 *
 * We don't block billing over it. Straight line x 1.4 is a fair guess for city roads; the
 * segment (and so the trip) is marked DEGRADED and gets recomputed once routing is back.
 */
public final class RoutingUnavailableRule implements SegmentRule {

    @Override
    public Optional<SegmentDecision> apply(SegmentContext segment) {
        if (!(segment.route() instanceof RouteAnswer.Unavailable)) {
            return Optional.empty();
        }
        double guessMetres = segment.aerialMetres() * segment.rules().degradedRouteFactor();
        return Optional.of(new SegmentDecision(SegmentLabel.DEGRADED_AERIAL, Distance.ofMetres(guessMetres)));
    }
}
