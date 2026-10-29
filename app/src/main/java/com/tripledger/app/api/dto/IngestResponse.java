package com.tripledger.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "What an upload added, and where the trip stands now")
public record IngestResponse(
        String tripId,
        @Schema(description = "RECEIVED, COMPUTED or DEGRADED") String status,
        @Schema(description = "true = same Idempotency-Key and body as an earlier upload; nothing was redone") boolean replayed,
        int pingsReceived,
        int pingsNew,
        @Schema(description = "pings we already had (retries, overlapping uploads) - ignored, never double counted") int pingsDuplicate,
        int milestonesNew,
        int plannedStopsNew) {
}
