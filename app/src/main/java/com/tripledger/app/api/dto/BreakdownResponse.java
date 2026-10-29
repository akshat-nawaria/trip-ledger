package com.tripledger.app.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Every piece of the trip, with the rule that measured it")
public record BreakdownResponse(
        String tripId,
        String status,
        String billableKm,
        String ruleVersion,
        UUID runId,
        @Schema(description = "how many segments each rule decided, e.g. {\"SHORT_GAP_AERIAL\": 320, \"ROUTED\": 2}")
        Map<String, Long> ruleCounts,
        @Schema(description = "pings received, kept, and removed per cleaning filter") JsonNode cleaning,
        @Schema(description = "anchor points used (pickups, drops, office) and where each came from") JsonNode anchors,
        List<SegmentOut> segments) {

    public record SegmentOut(
            int seq,
            Instant from,
            Instant to,
            double fromLat,
            double fromLon,
            double toLat,
            double toLon,
            long distanceMm,
            @Schema(example = "0.050") String km,
            @Schema(example = "SHORT_GAP_AERIAL") String rule,
            @Schema(description = "rejected pieces are paid as a straight line; a person may want to look") boolean needsReview) {
    }
}
