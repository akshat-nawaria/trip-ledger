package com.tripledger.core.rules;

import com.tripledger.core.engine.RouteAnswer;
import com.tripledger.core.model.SegmentLabel;
import java.util.Optional;

// Rule 6. Everything checked out - pay the real driving distance. This is the normal case for gaps.
public final class RoutedRule implements SegmentRule {

    @Override
    public Optional<SegmentDecision> apply(SegmentContext segment) {
        if (segment.route() instanceof RouteAnswer.Found found) {
            return Optional.of(new SegmentDecision(SegmentLabel.ROUTED, found.drivingDistance()));
        }
        return Optional.empty();
    }
}
