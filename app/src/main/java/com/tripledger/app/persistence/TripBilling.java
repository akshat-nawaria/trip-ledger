package com.tripledger.app.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// the billing side of a trip: which invoice it went on, and the run + km that were billed then
public record TripBilling(String invoiceId, UUID billedRunId, BigDecimal billedKm, Instant billedAt) {

    public static final TripBilling NOT_BILLED = new TripBilling(null, null, null, null);

    public boolean isBilled() {
        return invoiceId != null;
    }
}
