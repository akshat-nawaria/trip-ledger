package com.tripledger.core.rules;

import com.tripledger.core.model.SegmentLabel;

/**
 * Rule 2. Long gap, but the cab barely moved (under 5 km/h) - it was waiting at a pickup,
 * stuck at a gate, etc. Pay the tiny straight line and nothing more. Routing this would
 * mostly turn GPS wobble into fake kilometres.
 */
public final class ParkedRule extends AbstractAerialRule {

    @Override
    protected boolean matches(SegmentContext segment) {
        return segment.aerialSpeedKmph() < segment.rules().parkedSpeedKmph();
    }

    @Override
    protected SegmentLabel label() {
        return SegmentLabel.PARKED;
    }
}
