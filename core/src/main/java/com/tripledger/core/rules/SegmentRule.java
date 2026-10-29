package com.tripledger.core.rules;

import java.util.Optional;

/**
 * One rule in the chain. Returns a decision if it applies to this segment, or empty
 * to say "not my case, ask the next rule".
 */
public interface SegmentRule {

    Optional<SegmentDecision> apply(SegmentContext segment);
}
