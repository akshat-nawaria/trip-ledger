package com.tripledger.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripledger.app.api.dto.EvidenceRequest;
import com.tripledger.app.persistence.TripRepository;
import com.tripledger.app.persistence.TripState;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/**
 * What happens when a device uploads evidence:
 *   check the request -> store it (EvidenceWriter, one transaction) -> compute the km right away.
 *
 * Computing straight after the upload keeps S5 simple and easy to demo. In S7 the compute moves to a
 * background job queue; the API stays exactly the same (clients read the km with GET /distance).
 *
 * If the compute itself fails, the evidence is already safely stored and the trip stays RECEIVED,
 * so nothing is lost - it just gets computed on the next upload (or by the S7 worker).
 */
@Service
public class EvidenceService {

    private final EvidenceWriter writer;
    private final DistanceService distance;
    private final TripRepository trips;
    private final ObjectMapper json;

    public EvidenceService(EvidenceWriter writer, DistanceService distance, TripRepository trips, ObjectMapper json) {
        this.writer = writer;
        this.distance = distance;
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

        // a replay changed nothing, so there's nothing to compute - just report where the trip is
        TripState status = stored.replayed()
                ? trips.find(tenant, tripId).orElseThrow(() -> new TripNotFoundException(tripId)).status()
                : distance.compute(tenant, tripId).status();
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
