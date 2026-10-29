package com.tripledger.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

// km is a string on purpose ("19.12"), so no JSON client turns it into 19.119999999 on the way
@Schema(description = "The billable distance of a trip and how it was obtained")
public record DistanceResponse(
        String tripId,
        @Schema(description = "RECEIVED, COMPUTED or DEGRADED") String status,
        @Schema(description = "km to bill, 2 decimals, rounded once (HALF_EVEN). Null until computed", example = "30.61")
        String billableKm,
        @Schema(description = "exact total in millimetres") Long totalMm,
        String ruleVersion,
        @Schema(description = "which version of the tenant's billing settings was used (0 = case-study defaults)")
        Integer configVersion,
        Routing routing,
        UUID runId,
        Instant computedAt) {

    @Schema(description = "What routing cost for this computation")
    public record Routing(int requests, int cacheHits, int providerCalls, int unavailable) {
    }
}
