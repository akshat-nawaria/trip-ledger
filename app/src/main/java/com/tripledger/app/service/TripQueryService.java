package com.tripledger.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripledger.app.api.dto.BreakdownResponse;
import com.tripledger.app.api.dto.DistanceResponse;
import com.tripledger.app.api.dto.TripSummary;
import com.tripledger.app.persistence.EvidenceRepository;
import com.tripledger.app.persistence.RunRepository;
import com.tripledger.app.persistence.RunRow;
import com.tripledger.app.persistence.SegmentRow;
import com.tripledger.app.persistence.TripRepository;
import com.tripledger.app.persistence.TripRow;
import com.tripledger.app.persistence.TripState;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

// the read side: everything the GET endpoints show. Never computes anything, only reads saved runs.
@Service
public class TripQueryService {

    public static final int MAX_LIST_LIMIT = 200;

    private final TripRepository trips;
    private final RunRepository runs;
    private final EvidenceRepository evidence;
    private final ObjectMapper json;

    public TripQueryService(TripRepository trips, RunRepository runs, EvidenceRepository evidence, ObjectMapper json) {
        this.trips = trips;
        this.runs = runs;
        this.evidence = evidence;
        this.json = json;
    }

    // onlyForEmployee: set when the caller is a plain employee. A trip they didn't ride in gets the
    // same 404 as a trip that doesn't exist - we don't even confirm it's there.
    public DistanceResponse distance(String tenant, String tripId, Optional<String> onlyForEmployee) {
        TripRow trip = findTrip(tenant, tripId);
        if (onlyForEmployee.isPresent() && !evidence.involvesEmployee(tenant, tripId, onlyForEmployee.get())) {
            throw new TripNotFoundException(tripId);
        }
        Optional<RunRow> run = runs.latest(tenant, tripId);
        if (run.isEmpty()) {
            return new DistanceResponse(tripId, trip.status().name(), null, null, null, null, null, null, null);
        }
        RunRow r = run.get();
        return new DistanceResponse(tripId, trip.status().name(), km(r.billableKm()), r.totalMm(), r.ruleVersion(),
                r.configVersion(),
                new DistanceResponse.Routing(r.routeRequests(), r.cacheHits(), r.providerCalls(), r.routingUnavailable(),
                        r.routesReplayed()),
                r.id(), r.createdAt());
    }

    public BreakdownResponse breakdown(String tenant, String tripId) {
        TripRow trip = findTrip(tenant, tripId);
        Optional<RunRow> run = runs.latest(tenant, tripId);
        if (run.isEmpty()) {
            return new BreakdownResponse(tripId, trip.status().name(), null, null, null, Map.of(), null, null, List.of());
        }
        RunRow r = run.get();
        List<SegmentRow> segments = runs.segments(r.id());

        Map<String, Long> ruleCounts = new LinkedHashMap<>();
        segments.forEach(s -> ruleCounts.merge(s.rule().name(), 1L, Long::sum));

        List<BreakdownResponse.SegmentOut> out = segments.stream().map(s -> new BreakdownResponse.SegmentOut(
                s.seq(), s.fromTime(), s.toTime(),
                s.from().latitude(), s.from().longitude(), s.to().latitude(), s.to().longitude(),
                s.distanceMm(), segmentKm(s.distanceMm()), s.rule().name(), s.rule().needsReview())).toList();

        return new BreakdownResponse(tripId, trip.status().name(), km(r.billableKm()), r.ruleVersion(), r.id(),
                ruleCounts, parse(r.cleaningJson()), parse(r.anchorsJson()), out);
    }

    public List<TripSummary> list(String tenant, TripState status, int limit) {
        if (limit < 1 || limit > MAX_LIST_LIMIT) {
            throw new InvalidRequestException("limit must be between 1 and " + MAX_LIST_LIMIT);
        }
        return trips.list(tenant, status, limit).stream()
                .map(t -> new TripSummary(t.tripId(), t.status().name(), t.updatedAt()))
                .toList();
    }

    private TripRow findTrip(String tenant, String tripId) {
        RequestChecks.tripId(tripId);
        return trips.find(tenant, tripId).orElseThrow(() -> new TripNotFoundException(tripId));
    }

    private static String km(BigDecimal billableKm) {
        return billableKm.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    // per-segment display only (3 decimals = metres). The bill itself is rounded once, on the total.
    private static String segmentKm(long millimetres) {
        return BigDecimal.valueOf(millimetres).movePointLeft(6).setScale(3, RoundingMode.HALF_EVEN).toPlainString();
    }

    private JsonNode parse(String text) {
        try {
            return json.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored run explanation is not valid JSON", e);
        }
    }
}
