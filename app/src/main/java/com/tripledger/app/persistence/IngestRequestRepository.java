package com.tripledger.app.persistence;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// remembers every Idempotency-Key with a hash of the body it came with
@Repository
public class IngestRequestRepository {

    private final JdbcTemplate jdbc;

    public IngestRequestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> findRequestHash(String tenant, String tripId, String idempotencyKey) {
        return jdbc.queryForList("""
                SELECT request_hash FROM ingest_request
                WHERE tenant_id = ? AND trip_id = ? AND idempotency_key = ?
                """, String.class, tenant, tripId, idempotencyKey).stream().findFirst();
    }

    public void insert(String tenant, String tripId, String idempotencyKey, String requestHash) {
        jdbc.update("""
                INSERT INTO ingest_request (tenant_id, trip_id, idempotency_key, request_hash)
                VALUES (?, ?, ?, ?)
                """, tenant, tripId, idempotencyKey, requestHash);
    }
}
