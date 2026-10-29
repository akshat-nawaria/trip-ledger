package com.tripledger.app.service;

import com.tripledger.app.persistence.AdjustmentRepository;
import com.tripledger.app.persistence.RunRepository;
import com.tripledger.app.persistence.RunRow;
import com.tripledger.app.persistence.TripBilling;
import com.tripledger.app.persistence.TripRepository;
import com.tripledger.app.persistence.TripRow;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Putting a trip on an invoice, and reading what changed since (R12).
 *
 * Billing freezes the trip's current run and km. From then on nothing edits that invoice:
 * a later computation that moves the km (late pings, recovery after an outage, new settings)
 * adds a signed adjustment line instead - see ResultWriter.
 *
 * A DEGRADED trip can be billed too. Its km are then provisional (straight line x 1.4), and the
 * recompute after recovery settles the difference as an adjustment. Billing never waits on the
 * map provider.
 */
@Service
public class BillingService {

    public record AdjustmentsView(TripBilling billing, BigDecimal chargedKm, List<AdjustmentRepository.Adjustment> adjustments) {
    }

    private final TripRepository trips;
    private final RunRepository runs;
    private final AdjustmentRepository adjustments;

    public BillingService(TripRepository trips, RunRepository runs, AdjustmentRepository adjustments) {
        this.trips = trips;
        this.runs = runs;
        this.adjustments = adjustments;
    }

    // idempotent: billing the same trip on the same invoice again just returns what's there
    @Transactional
    public TripBilling bill(String tenant, String tripId, String invoiceId) {
        RequestChecks.tripId(tripId);
        RequestChecks.invoiceId(invoiceId);
        TripBilling current = trips.lockBilling(tenant, tripId).orElseThrow(() -> new TripNotFoundException(tripId));
        if (current.isBilled()) {
            if (current.invoiceId().equals(invoiceId)) {
                return current;
            }
            throw new BillingConflictException("Trip '" + tripId + "' is already on invoice '" + current.invoiceId()
                    + "'. Later changes show up as adjustments, not on a second invoice.");
        }
        RunRow run = runs.latest(tenant, tripId).orElseThrow(() -> new BillingConflictException(
                "Trip '" + tripId + "' has no computed distance yet, so there is nothing to bill."));
        trips.markBilled(tenant, tripId, invoiceId, run.id(), run.billableKm());
        return trips.lockBilling(tenant, tripId).orElseThrow(() -> new TripNotFoundException(tripId));
    }

    public AdjustmentsView adjustments(String tenant, String tripId) {
        RequestChecks.tripId(tripId);
        TripRow trip = trips.find(tenant, tripId).orElseThrow(() -> new TripNotFoundException(tripId));
        TripBilling billing = trips.billing(tenant, trip.tripId());
        List<AdjustmentRepository.Adjustment> lines = adjustments.list(tenant, tripId);
        BigDecimal charged = null;
        if (billing.isBilled()) {
            charged = billing.billedKm();
            for (AdjustmentRepository.Adjustment a : lines) {
                charged = charged.add(a.deltaKm());
            }
        }
        return new AdjustmentsView(billing, charged, lines);
    }
}
