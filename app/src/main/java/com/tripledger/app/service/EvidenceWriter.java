package com.tripledger.app.service;

import com.tripledger.app.api.dto.EvidenceRequest;
import com.tripledger.app.persistence.EvidenceRepository;
import com.tripledger.app.persistence.IngestRequestRepository;
import com.tripledger.app.persistence.JobRepository;
import com.tripledger.app.persistence.TripRepository;
import com.tripledger.app.persistence.TripRow;
import com.tripledger.core.model.GeoPoint;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores one upload, in one transaction. Either all of it lands or none of it does.
 *
 * Order matters here:
 *   1. make sure the trip row exists, then lock it - uploads for the same trip now queue up
 *   2. the trip details must match the first upload
 *   3. Idempotency-Key: seen with the same body -> replay, seen with another body -> 409
 *   4. append the evidence (duplicates are silently skipped by the database)
 *   5. if anything new arrived, the km on file are stale -> status RECEIVED
 *   6. queue a compute job - in this same transaction, so evidence and its job can never be split up
 *      (if the app dies a millisecond after the commit, the job is still there for the worker)
 */
@Service
public class EvidenceWriter {

    private final TripRepository trips;
    private final EvidenceRepository evidence;
    private final IngestRequestRepository ingestRequests;
    private final JobRepository jobs;

    public EvidenceWriter(TripRepository trips, EvidenceRepository evidence, IngestRequestRepository ingestRequests,
            JobRepository jobs) {
        this.trips = trips;
        this.evidence = evidence;
        this.ingestRequests = ingestRequests;
        this.jobs = jobs;
    }

    @Transactional
    public StoredEvidence store(String tenant, String tripId, String idempotencyKey, String requestHash,
            EvidenceRequest request) {
        Instant windowStart = request.windowStartInstant();
        Instant windowEnd = request.windowEndInstant();
        GeoPoint office = request.plannedOfficePoint();

        trips.insertIfAbsent(tenant, tripId, request.tripType(), windowStart, windowEnd, office);
        TripRow trip = trips.lock(tenant, tripId).orElseThrow(() -> new IllegalStateException("trip vanished: " + tripId));
        if (!trip.sameDetailsAs(request.tripType(), windowStart, windowEnd, office)) {
            throw new EvidenceConflictException("Trip '" + tripId
                    + "' already exists with a different type, window or office. Every upload of a trip must agree on those.");
        }

        Optional<String> earlier = ingestRequests.findRequestHash(tenant, tripId, idempotencyKey);
        if (earlier.isPresent()) {
            if (earlier.get().equals(requestHash)) {
                return StoredEvidence.replay(request.pings().size());
            }
            throw new EvidenceConflictException("Idempotency-Key '" + idempotencyKey
                    + "' was already used for a different upload. Use a new key for new data.");
        }
        ingestRequests.insert(tenant, tripId, idempotencyKey, requestHash);

        int pingsNew = evidence.insertPings(tenant, tripId, request.toPings());
        int milestonesNew = evidence.insertMilestones(tenant, tripId, request.toMilestones());
        int stopsNew = evidence.insertPlannedStops(tenant, tripId, request.toPlannedStops());

        if (pingsNew + milestonesNew + stopsNew > 0) {
            trips.markReceived(tenant, tripId);
        }
        // queued even when nothing was new: a DEGRADED trip or changed tenant settings still deserve a
        // fresh look, and if nothing changed at all the fingerprint makes the job a cheap no-op
        jobs.enqueue(tenant, tripId, JobRepository.Reason.UPLOAD);
        return new StoredEvidence(false, request.pings().size(), pingsNew, milestonesNew, stopsNew);
    }
}
