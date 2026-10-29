package com.tripledger.core.model;

import com.tripledger.core.geo.Distance;
import java.util.Objects;

// one piece of the trip between two consecutive track points, with the rule that measured it
public record Segment(int seq, TrackPoint from, TrackPoint to, Distance distance, SegmentLabel label) {

    public Segment {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(distance, "distance");
        Objects.requireNonNull(label, "label");
    }
}
