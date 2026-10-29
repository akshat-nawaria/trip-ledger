package com.tripledger.app.api;

import com.tripledger.app.api.dto.BreakdownResponse;
import com.tripledger.app.api.dto.DistanceResponse;
import com.tripledger.app.api.dto.EvidenceRequest;
import com.tripledger.app.api.dto.IngestResponse;
import com.tripledger.app.api.dto.TripSummary;
import com.tripledger.app.persistence.TripState;
import com.tripledger.app.security.Caller;
import com.tripledger.app.security.CurrentCaller;
import com.tripledger.app.service.EvidenceService;
import com.tripledger.app.service.StoredEvidence;
import com.tripledger.app.service.TripQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// thin on purpose: read the request, call a service, shape the response. No business logic in here.
// Who may call which endpoint is decided in SecurityConfig; the tenant always comes from the caller's token.
@RestController
@RequestMapping("/v1/trips")
@Tag(name = "Trips", description = "Upload trip evidence, read the billable km and its explanation")
public class TripController {

    private final EvidenceService evidence;
    private final TripQueryService queries;
    private final CurrentCaller caller;

    public TripController(EvidenceService evidence, TripQueryService queries, CurrentCaller caller) {
        this.evidence = evidence;
        this.queries = queries;
        this.caller = caller;
    }

    @PostMapping("/{tripId}/evidence")
    @Operation(summary = "Upload pings, milestones and plan for a trip",
            description = "Safe to retry: re-sending with the same Idempotency-Key is answered without redoing anything, "
                    + "and pings we already have are never counted twice. A trip can be uploaded in several parts.")
    public ResponseEntity<IngestResponse> uploadEvidence(
            @PathVariable String tripId,
            @Parameter(description = "Unique per upload, e.g. a UUID. Re-use it only when retrying the exact same upload.")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody EvidenceRequest request) {
        EvidenceService.IngestResult result = evidence.ingest(caller.get().tenant(), tripId, idempotencyKey, request);
        StoredEvidence stored = result.stored();
        IngestResponse body = new IngestResponse(tripId, result.status().name(), stored.replayed(),
                stored.pingsReceived(), stored.pingsNew(), stored.pingsDuplicate(),
                stored.milestonesNew(), stored.plannedStopsNew());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @GetMapping("/{tripId}/distance")
    @Operation(summary = "Billable km for a trip, with status, rule version and routing calls used",
            description = "Employees only see trips they rode in; any other trip is reported as not found.")
    public DistanceResponse distance(@PathVariable String tripId) {
        Caller me = caller.get();
        return queries.distance(me.tenant(), tripId, me.onlyTripsOf());
    }

    @GetMapping("/{tripId}/breakdown")
    @Operation(summary = "Every segment of the trip with the rule that measured it, plus cleaning and anchor details")
    public BreakdownResponse breakdown(@PathVariable String tripId) {
        return queries.breakdown(caller.get().tenant(), tripId);
    }

    @GetMapping
    @Operation(summary = "List trips, newest first. Use status=DEGRADED to see the recovery backlog")
    public List<TripSummary> list(
            @RequestParam(required = false) TripState status,
            @RequestParam(defaultValue = "50") int limit) {
        return queries.list(caller.get().tenant(), status, limit);
    }
}
