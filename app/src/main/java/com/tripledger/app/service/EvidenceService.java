package com.tripledger.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripledger.app.api.dto.EvidenceRequest;
import com.tripledger.app.jobs.ComputeWorker;
import com.tripledger.app.persistence.TripRepository;
import com.tripledger.app.persistence.TripState;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/**
 * What happens when a device uploads evidence:
 *   check the request -> store it + queue a compute job (EvidenceWriter, one transaction)
 *   -> try to run that job right away, so the response usually already has the km.
 *
 * "Usually": if the compute fails, or a background worker is busy with the same trip, the response
 * just says RECEIVED. Nothing is lost - the job stays in the queue and the worker finishes it
 * (with retries). Clients read the final km with GET /distance.
 */
@Service
public class EvidenceService {

    private final EvidenceWriter writer;
    private final ComputeWorker worker;
    private final TripRepository trips;
    private final ObjectMapper json;

    public EvidenceService(EvidenceWriter writer, ComputeWorker worker, TripRepository trips, ObjectMapper json) {
        this.writer = writer;
        this.worker = worker;
        this.trips = trips;
        this.json = json;
    }

    public record IngestResult(StoredEvidence stored, TripState status) {
    }

    public IngestResult ingest(String tenant, String tripId, String idempotencyKey, EvidenceRequest request) {
        RequestChecks.tripId(tripId);
        RequestChecks.idempotencyKey(idempotencyKey);
        RequestChecks.tripWindow(request.windowStartInstant(), request.windowEndInstant());

        StoredEvidence stored = writer.store(tenant, tripId, idempotencyKey, hash(request), request);

        // a replay changed nothing and queued nothing - just report where the trip is
        if (!stored.replayed()) {
            worker.runNow(tenant, tripId);
        }
        TripState status = trips.find(tenant, tripId).orElseThrow(() -> new TripNotFoundException(tripId)).status();
        return new IngestResult(stored, status);
    }

    // fingerprint of the request body, used to tell a genuine retry from a reused Idempotency-Key
    private String hash(EvidenceRequest request) {
        try {
            byte[] body = json.writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("could not hash the request", e);
        }
    }
}
