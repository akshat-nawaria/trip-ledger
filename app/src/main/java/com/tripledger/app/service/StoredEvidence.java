package com.tripledger.app.service;

// what one upload actually added. replayed = same Idempotency-Key and body as before, nothing was redone.
public record StoredEvidence(
        boolean replayed,
        int pingsReceived,
        int pingsNew,
        int milestonesNew,
        int plannedStopsNew) {

    static StoredEvidence replay(int pingsReceived) {
        return new StoredEvidence(true, pingsReceived, 0, 0, 0);
    }

    public int pingsDuplicate() {
        return replayed ? 0 : pingsReceived - pingsNew;
    }
}
