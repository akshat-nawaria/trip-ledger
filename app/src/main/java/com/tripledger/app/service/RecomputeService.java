package com.tripledger.app.service;

import com.tripledger.app.jobs.ComputeWorker;
import com.tripledger.app.persistence.JobRepository;
import com.tripledger.app.persistence.TripRepository;
import com.tripledger.app.persistence.TripRow;
import org.springframework.stereotype.Service;

/**
 * An admin asking "compute this trip again". Goes through the same job queue as everything else,
 * so it can never run at the same time as a background compute of the same trip.
 *
 * Asking twice is safe (R11): same evidence + same rules + same rule version gives the very same
 * run back - the fingerprint finds it, nothing is recomputed, no routing is paid for.
 */
@Service
public class RecomputeService {

    private final TripRepository trips;
    private final JobRepository jobs;
    private final ComputeWorker worker;

    public RecomputeService(TripRepository trips, JobRepository jobs, ComputeWorker worker) {
        this.trips = trips;
        this.jobs = jobs;
        this.worker = worker;
    }

    public TripRow recompute(String tenant, String tripId) {
        RequestChecks.tripId(tripId);
        trips.find(tenant, tripId).orElseThrow(() -> new TripNotFoundException(tripId));
        jobs.enqueue(tenant, tripId, JobRepository.Reason.MANUAL);
        worker.runNow(tenant, tripId);
        return trips.find(tenant, tripId).orElseThrow(() -> new TripNotFoundException(tripId));
    }
}
