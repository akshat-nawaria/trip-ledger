package com.tripledger.app.api.dto;

import java.time.Instant;

// one line of the trip list (e.g. the DEGRADED backlog)
public record TripSummary(String tripId, String status, Instant updatedAt) {
}
