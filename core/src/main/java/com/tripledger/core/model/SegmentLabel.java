package com.tripledger.core.model;

/**
 * Which rule measured a segment. Goes on every segment of every bill, so if a vendor asks
 * "why only 30.6 km?" we can answer piece by piece.
 */
public enum SegmentLabel {
    SHORT_GAP_AERIAL(false),
    PARKED(false),
    ROUTED(false),
    DEGRADED_AERIAL(false),
    REJECTED_SPEED(true),
    REJECTED_DISTANCE(true);

    private final boolean needsReview;

    SegmentLabel(boolean needsReview) {
        this.needsReview = needsReview;
    }

    // rejected pieces are paid as a plain straight line, a human can approve more if it was a real detour
    public boolean needsReview() {
        return needsReview;
    }
}
