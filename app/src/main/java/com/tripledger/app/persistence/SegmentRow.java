package com.tripledger.app.persistence;

import com.tripledger.core.model.GeoPoint;
import com.tripledger.core.model.SegmentLabel;
import java.time.Instant;

// one measured piece of a saved run
public record SegmentRow(
        int seq,
        Instant fromTime,
        GeoPoint from,
        Instant toTime,
        GeoPoint to,
        long distanceMm,
        SegmentLabel rule) {
}
