package com.tripledger.core.rules;

import com.tripledger.core.model.SegmentLabel;

/**
 * Rule 0. Even the straight line would need the cab to go faster than the limit.
 *
 * A road is never shorter than a straight line, so asking the map service would be wasted
 * money - the route would only be slower. Reject it right away, pay the straight line,
 * and flag it so someone can look.
 */
public final class ImpossibleSpeedRule extends AbstractAerialRule {

    @Override
    protected boolean matches(SegmentContext segment) {
        return segment.aerialSpeedKmph() > segment.rules().maxSpeedKmph();
    }

    @Override
    protected SegmentLabel label() {
        return SegmentLabel.REJECTED_SPEED;
    }
}
