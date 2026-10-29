package com.tripledger.app.persistence;

import com.tripledger.core.model.TripStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// one saved computation. cleaningJson / anchorsJson are the "why" behind the number, kept as JSON
// because nobody queries inside them - they're only ever shown back in the breakdown.
public record RunRow(
        UUID id,
        String tripId,
        String inputHash,
        String ruleVersion,
        TripStatus status,
        long totalMm,
        BigDecimal billableKm,
        int routeRequests,
        int cacheHits,
        int providerCalls,
        int routingUnavailable,
        String cleaningJson,
        String anchorsJson,
        Instant createdAt) {
}
