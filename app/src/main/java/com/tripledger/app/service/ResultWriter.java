package com.tripledger.app.service;

import com.tripledger.app.persistence.AdjustmentRepository;
import com.tripledger.app.persistence.RunRepository;
import com.tripledger.app.persistence.TripBilling;
import com.tripledger.app.persistence.TripRepository;
import com.tripledger.app.persistence.TripState;
import com.tripledger.app.routing.RoutingUsage;
import com.tripledger.core.model.TripDistance;
import com.tripledger.core.model.TripStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves a finished computation, all in one transaction:
 *   the run (+ segments + routes) -> the trip now points at it -> if the trip was already billed
 *   and the km moved, a signed adjustment line.
 *
 * The billed run and billed km on the trip are NEVER touched here. A closed invoice stays closed;
 * the difference goes on an adjustment for the next invoice instead.
 *
 * Only a clean (COMPUTED) result can create an adjustment. A DEGRADED result is a provisional
 * guess (straight line x 1.4) - we don't send money corrections based on a guess. When routing is
 * back and the trip is recomputed properly, THAT result settles the difference.
 */
@Service
public class ResultWriter {

    private final RunRepository runs;
    private final TripRepository trips;
    private final AdjustmentRepository adjustments;

    public ResultWriter(RunRepository runs, TripRepository trips, AdjustmentRepository adjustments) {
        this.runs = runs;
        this.trips = trips;
        this.adjustments = adjustments;
    }

    @Transactional
    public void save(String tenant, UUID runId, String inputHash, int configVersion, TripDistance result,
            RoutingUsage usage, int routesReplayed, String cleaningJson, String anchorsJson) {
        String tripId = result.tripId();
        // lock the trip first: billing and adjustments for one trip happen strictly one at a time
        TripBilling billing = trips.lockBilling(tenant, tripId).orElseThrow(() -> new TripNotFoundException(tripId));

        runs.insert(tenant, runId, inputHash, configVersion, result, usage, routesReplayed, cleaningJson, anchorsJson);
        trips.markComputed(tenant, tripId, TripState.from(result.status()), runId);

        if (billing.isBilled() && result.status() == TripStatus.COMPUTED) {
            addAdjustmentIfKmMoved(tenant, tripId, billing, runId, result);
        }
    }

    private void addAdjustmentIfKmMoved(String tenant, String tripId, TripBilling billing, UUID runId,
            TripDistance result) {
        AdjustmentRepository.Totals sofar = adjustments.totals(tenant, tripId);
        // what the customer has been charged so far: the invoice plus every earlier correction
        BigDecimal chargedKm = billing.billedKm().add(sofar.deltaKm());
        BigDecimal deltaKm = result.billableKm().subtract(chargedKm);
        if (deltaKm.signum() == 0) {
            return; // moved by a few metres at most, not a single billable 10 m step - nothing to correct
        }
        long chargedMm = runs.totalMm(billing.billedRunId()) + sofar.deltaMm();
        long deltaMm = result.total().millimetres() - chargedMm;
        UUID fromRun = sofar.lastRunId() != null ? sofar.lastRunId() : billing.billedRunId();
        adjustments.insert(UUID.randomUUID(), tenant, tripId, billing.invoiceId(), deltaKm, deltaMm, fromRun, runId);
    }
}
