package com.tripledger.core.rules;

import com.tripledger.core.model.SegmentLabel;

/**
 * Rule 1. The two points are less than 70 s apart (and the speed is fine, rule 0 already
 * checked that). Over such a short hop the road and the straight line are basically the same,
 * so there's no point paying for a routing call.
 */
public final class ShortGapRule extends AbstractAerialRule {

    @Override
    protected boolean matches(SegmentContext segment) {
        return segment.gap().compareTo(segment.rules().shortGap()) < 0;
    }

    @Override
    protected SegmentLabel label() {
        return SegmentLabel.SHORT_GAP_AERIAL;
    }
}
