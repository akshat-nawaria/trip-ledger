package com.tripledger.core.model;

import com.tripledger.core.cleaning.CleaningReport;
import com.tripledger.core.geo.Distance;
import java.math.BigDecimal;
import java.util.List;

/**
 * The engine's answer for one trip: the total, the bill-ready km, and enough detail
 * (segments, anchors, cleaning report, route answers used) to explain every metre.
 *
 * routeRequests = how many times the engine had to ask for a road distance. Short gaps,
 * parked time and impossible jumps never ask, which is where most of the savings come from.
 */
public record TripDistance(
        String tripId,
        String ruleSetVersion,
        Distance total,
        BigDecimal billableKm,
        TripStatus status,
        List<Segment> segments,
        List<Anchor> anchors,
        CleaningReport cleaning,
        int routeRequests,
        List<RouteEvidence> routesUsed) {

    public TripDistance {
        segments = List.copyOf(segments);
        anchors = List.copyOf(anchors);
        routesUsed = List.copyOf(routesUsed);
    }

    public long segmentsWith(SegmentLabel label) {
        return segments.stream().filter(s -> s.label() == label).count();
    }
}
