package com.tripledger.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripledger.app.observability.TripLedgerMetrics;
import com.tripledger.app.persistence.EvidenceRepository;
import com.tripledger.app.persistence.RunRepository;
import com.tripledger.app.persistence.RunRow;
import com.tripledger.app.persistence.TenantSettings;
import com.tripledger.app.persistence.TripRepository;
import com.tripledger.app.persistence.TripRow;
import com.tripledger.app.persistence.TripState;
import com.tripledger.app.routing.PinnedRoutes;
import com.tripledger.app.routing.RoutingGateway;
import com.tripledger.app.routing.TripRouting;
import com.tripledger.core.EngineInfo;
import com.tripledger.core.config.BillingRules;
import com.tripledger.core.engine.DistanceEngine;
import com.tripledger.core.engine.EvidenceFingerprint;
import com.tripledger.core.model.TripDistance;
import com.tripledger.core.model.TripEvidence;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Turns everything stored for a trip into km, and saves the result as a new run.
 *
 *   load evidence + the tenant's settings -> fingerprint both -> already have a good run for exactly this? reuse it
 *                                                              -> otherwise run the engine (with routing) and save
 *
 * The settings are part of the fingerprint, so after a tenant changes them the next upload of a
 * trip is recomputed under the new settings instead of reusing the old answer.
 *
 * Road distances an earlier run of the trip already paid for are replayed (PinnedRoutes), so a
 * recompute only asks the provider about gaps that are actually new.
 *
 * Note there's no transaction around the engine on purpose: it may wait on the routing provider,
 * and holding a DB transaction open while waiting on the network is how you run out of connections.
 * The save at the end is its own short transaction (ResultWriter.save).
 */
@Service
public class DistanceService {

    private final TripRepository trips;
    private final EvidenceRepository evidence;
    private final RunRepository runs;
    private final ResultWriter results;
    private final TenantConfigService tenantConfig;
    private final RoutingGateway routing;
    private final ObjectMapper json;
    private final TripLedgerMetrics metrics;

    public DistanceService(TripRepository trips, EvidenceRepository evidence, RunRepository runs, ResultWriter results,
            TenantConfigService tenantConfig, RoutingGateway routing, ObjectMapper json, TripLedgerMetrics metrics) {
        this.trips = trips;
        this.evidence = evidence;
        this.runs = runs;
        this.results = results;
        this.tenantConfig = tenantConfig;
        this.routing = routing;
        this.json = json;
        this.metrics = metrics;
    }

    public record Outcome(TripState status, UUID runId, boolean reused) {
    }

    public Outcome compute(String tenant, String tripId) {
        TripRow trip = trips.find(tenant, tripId).orElseThrow(() -> new TripNotFoundException(tripId));
        TripEvidence tripEvidence = new TripEvidence(tripId, trip.tripType(), trip.windowStart(), trip.windowEnd(),
                trip.office(), evidence.pings(tenant, tripId), evidence.milestones(tenant, tripId),
                evidence.plannedStops(tenant, tripId));
        TenantSettings settings = tenantConfig.current(tenant);
        BillingRules rules = settings.toRules();
        String inputHash = EvidenceFingerprint.of(tripEvidence, rules, EngineInfo.RULE_SET_VERSION);

        // same evidence, same rules, and we already got a clean answer -> it can't come out different,
        // so don't spend routing calls finding that out again
        Optional<RunRow> alreadyDone = runs.findComputed(tenant, tripId, inputHash, EngineInfo.RULE_SET_VERSION);
        if (alreadyDone.isPresent()) {
            metrics.reused();
            return reuse(tenant, trip, alreadyDone.get());
        }

        var timer = metrics.startCompute();
        TripRouting tripRouting = routing.newTrip();
        PinnedRoutes lookup = new PinnedRoutes(runs.pinnedRoutes(tenant, tripId), tripRouting);
        // the engine holds no state, so a fresh one per computation (with this tenant's rules) costs nothing
        TripDistance result = new DistanceEngine(rules).compute(tripEvidence, lookup);
        UUID runId = UUID.randomUUID();
        try {
            results.save(tenant, runId, inputHash, settings.version(), result, tripRouting.usage(), lookup.replayed(),
                    toJson(RunExplanation.cleaningOf(result)), toJson(RunExplanation.anchorsOf(result)));
        } catch (DuplicateKeyException e) {
            // two requests computed the same trip at the same moment and the other one saved first.
            // Its answer is identical by definition, so just point at it.
            RunRow theirs = runs.findComputed(tenant, tripId, inputHash, EngineInfo.RULE_SET_VERSION).orElseThrow(() -> e);
            return reuse(tenant, trip, theirs);
        }
        metrics.computed(timer, result, tripRouting.usage(), lookup.replayed());
        return new Outcome(TripState.from(result.status()), runId, false);
    }

    private Outcome reuse(String tenant, TripRow trip, RunRow run) {
        if (!run.id().equals(trip.latestRunId()) || trip.status() != TripState.COMPUTED) {
            trips.markComputed(tenant, trip.tripId(), TripState.COMPUTED, run.id());
        }
        return new Outcome(TripState.COMPUTED, run.id(), true);
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not write run explanation as JSON", e);
        }
    }
}
