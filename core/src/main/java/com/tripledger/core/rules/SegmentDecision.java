package com.tripledger.core.rules;

import com.tripledger.core.geo.Distance;
import com.tripledger.core.model.SegmentLabel;

// what a rule decided for one segment: how long it is and why
public record SegmentDecision(SegmentLabel label, Distance distance) {
}
