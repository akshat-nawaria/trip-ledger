package com.tripledger.app.service;

// -> 409. The upload clashes with what we already have: an Idempotency-Key reused for a different
// body, or trip details (type, window, office) that don't match the first upload.
public class EvidenceConflictException extends RuntimeException {

    public EvidenceConflictException(String message) {
        super(message);
    }
}
