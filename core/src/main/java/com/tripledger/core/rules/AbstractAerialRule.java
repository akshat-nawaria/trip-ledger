package com.tripledger.core.rules;

import com.tripledger.core.model.SegmentLabel;
import java.util.Optional;

/**
 * Base for the rules that just pay the straight line (rules 0, 1 and 2).
 * Subclasses only say *when* they apply and which label to use - the measuring is the same.
 */
public abstract class AbstractAerialRule implements SegmentRule {

    protected abstract boolean matches(SegmentContext segment);

    protected abstract SegmentLabel label();

    @Override
    public final Optional<SegmentDecision> apply(SegmentContext segment) {
        if (!matches(segment)) {
            return Optional.empty();
        }
        return Optional.of(new SegmentDecision(label(), segment.aerialDistance()));
    }
}
