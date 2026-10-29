package com.tripledger.app.api.dto;

import com.tripledger.app.persistence.AdjustmentRepository;
import com.tripledger.app.persistence.TripBilling;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// request / response shapes for recompute, billing and adjustments. km are strings ("+1.80") so
// no JSON client ever rounds them on the way.
public final class BillingDtos {

    private BillingDtos() {
    }

    @Schema(description = "Where a trip stands after a recompute request")
    public record RecomputeResponse(String tripId, String status, UUID runId) {
    }

    public record BillRequest(@NotBlank @Schema(example = "INV-2026-10-0042") String invoiceId) {
    }

    @Schema(description = "The invoice a trip is on, frozen at the moment it was billed")
    public record BillingResponse(String tripId, String invoiceId, String billedKm, UUID billedRunId, Instant billedAt) {

        public static BillingResponse of(String tripId, TripBilling b) {
            return new BillingResponse(tripId, b.invoiceId(), km(b.billedKm()), b.billedRunId(), b.billedAt());
        }
    }

    @Schema(description = "Signed corrections to a billed trip, for the next invoice")
    public record AdjustmentsResponse(
            String tripId,
            @Schema(description = "null = not billed yet") String invoiceId,
            String billedKm,
            @Schema(description = "billed km + all adjustments = what the customer has been charged in total")
            String chargedKm,
            List<AdjustmentOut> adjustments) {
    }

    public record AdjustmentOut(UUID id, String invoiceId,
            @Schema(example = "+1.80") String deltaKm, long deltaMm, UUID fromRunId, UUID toRunId, Instant createdAt) {

        public static AdjustmentOut of(AdjustmentRepository.Adjustment a) {
            String km = km(a.deltaKm());
            return new AdjustmentOut(a.id(), a.invoiceId(), a.deltaKm().signum() > 0 ? "+" + km : km, a.deltaMm(),
                    a.fromRunId(), a.toRunId(), a.createdAt());
        }
    }

    public static String km(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }
}
