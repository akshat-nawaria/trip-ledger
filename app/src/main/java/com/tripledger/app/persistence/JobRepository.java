package com.tripledger.app.persistence;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The compute queue (table compute_job). Every claim is a single UPDATE ... RETURNING, so it's
 * atomic on its own, and "FOR UPDATE SKIP LOCKED" lets many workers pull from the same table
 * without ever grabbing the same job twice or waiting on each other.
 */
@Repository
public class JobRepository {

    public enum Reason { UPLOAD, RECOVERY, MANUAL }

    public record ClaimedJob(long id, String tenantId, String tripId, String reason, int attempts) {
    }

    // a job is claimable when it's waiting and due, or when its worker's lease ran out (worker crashed).
    // Never hand out a trip that another worker is busy with right now - one computation per trip at a time.
    private static final String CLAIMABLE = """
            ((c.status = 'PENDING' AND c.run_after <= now()) OR (c.status = 'RUNNING' AND c.lease_until < now()))
            AND NOT EXISTS (SELECT 1 FROM compute_job r
                            WHERE r.tenant_id = c.tenant_id AND r.trip_id = c.trip_id
                              AND r.status = 'RUNNING' AND r.lease_until >= now() AND r.id <> c.id)
            """;

    private static final String CLAIM_SET = """
            UPDATE compute_job j
            SET status = 'RUNNING', lease_until = now() + CAST(? AS interval),
                attempts = j.attempts + 1, updated_at = now()
            """;

    private final JdbcTemplate jdbc;

    public JobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // false = there was already a waiting job for this trip, which will see the new evidence anyway
    public boolean enqueue(String tenant, String tripId, Reason reason) {
        return jdbc.update("""
                INSERT INTO compute_job (tenant_id, trip_id, reason) VALUES (?, ?, ?)
                ON CONFLICT (tenant_id, trip_id) WHERE status = 'PENDING' DO NOTHING
                """, tenant, tripId, reason.name()) == 1;
    }

    // the background worker: up to `max` due jobs from any tenant, oldest first
    public List<ClaimedJob> claim(int max, Duration lease) {
        return jdbc.query(CLAIM_SET + """
                WHERE j.id IN (SELECT c.id FROM compute_job c WHERE
                """ + CLAIMABLE + """
                               ORDER BY c.id LIMIT ? FOR UPDATE SKIP LOCKED)
                RETURNING j.id, j.tenant_id, j.trip_id, j.reason, j.attempts
                """, (rs, n) -> new ClaimedJob(rs.getLong("id"), rs.getString("tenant_id"), rs.getString("trip_id"),
                        rs.getString("reason"), rs.getInt("attempts")),
                interval(lease), max);
    }

    // the "do it right now" path after an upload: the waiting job of one particular trip, if nobody has it.
    // Ignores run_after on purpose - a person is waiting for this answer.
    public Optional<ClaimedJob> claimForTrip(String tenant, String tripId, Duration lease) {
        return jdbc.query(CLAIM_SET + """
                WHERE j.id = (SELECT c.id FROM compute_job c
                              WHERE c.tenant_id = ? AND c.trip_id = ? AND c.status = 'PENDING'
                                AND NOT EXISTS (SELECT 1 FROM compute_job r
                                                WHERE r.tenant_id = c.tenant_id AND r.trip_id = c.trip_id
                                                  AND r.status = 'RUNNING' AND r.lease_until >= now())
                              ORDER BY c.id LIMIT 1 FOR UPDATE SKIP LOCKED)
                RETURNING j.id, j.tenant_id, j.trip_id, j.reason, j.attempts
                """, (rs, n) -> new ClaimedJob(rs.getLong("id"), rs.getString("tenant_id"), rs.getString("trip_id"),
                        rs.getString("reason"), rs.getInt("attempts")),
                interval(lease), tenant, tripId).stream().findFirst();
    }

    public void complete(long jobId) {
        jdbc.update("UPDATE compute_job SET status = 'DONE', lease_until = NULL, updated_at = now() WHERE id = ?", jobId);
    }

    public void fail(long jobId, String error) {
        jdbc.update("""
                UPDATE compute_job SET status = 'FAILED', lease_until = NULL, last_error = ?, updated_at = now()
                WHERE id = ?
                """, error, jobId);
    }

    // back to the queue, but not before `delay` has passed. If a newer job for the same trip is already
    // waiting, that one will do the work - so this one just steps aside instead of clashing with it.
    public void retryLater(long jobId, Duration delay, String error) {
        try {
            jdbc.update("""
                    UPDATE compute_job SET status = 'PENDING', run_after = now() + CAST(? AS interval),
                        lease_until = NULL, last_error = ?, updated_at = now()
                    WHERE id = ?
                    """, interval(delay), error, jobId);
        } catch (DuplicateKeyException e) {
            jdbc.update("""
                    UPDATE compute_job SET status = 'DONE', lease_until = NULL,
                        last_error = 'superseded by a newer job for the same trip', updated_at = now()
                    WHERE id = ?
                    """, jobId);
        }
    }

    // trips that deserve another go once routing works again: DEGRADED ones, plus any RECEIVED trip that
    // somehow never got a job (e.g. evidence stored before the job queue existed). Returns how many were queued.
    // Trips that already have a job waiting or running are skipped BEFORE the limit is applied - otherwise a
    // few old trips with retries pending could fill a small trial batch every time and nothing new would run.
    public int enqueueRecovery(int limit) {
        return jdbc.update("""
                INSERT INTO compute_job (tenant_id, trip_id, reason)
                SELECT t.tenant_id, t.trip_id, 'RECOVERY' FROM trip t
                WHERE (t.status = 'DEGRADED' AND NOT EXISTS (SELECT 1 FROM compute_job j
                                                             WHERE j.tenant_id = t.tenant_id AND j.trip_id = t.trip_id
                                                               AND j.status IN ('PENDING', 'RUNNING')))
                   OR (t.status = 'RECEIVED' AND NOT EXISTS (SELECT 1 FROM compute_job j
                                                             WHERE j.tenant_id = t.tenant_id AND j.trip_id = t.trip_id))
                ORDER BY t.updated_at
                LIMIT ?
                ON CONFLICT (tenant_id, trip_id) WHERE status = 'PENDING' DO NOTHING
                """, limit);
    }

    // ---- numbers for the monitoring gauges and the admin page ----

    public long countByStatus(String status) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM compute_job WHERE status = ?", Long.class, status);
        return n == null ? 0 : n;
    }

    public long countFailedLastDay() {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM compute_job WHERE status = 'FAILED' AND updated_at > now() - interval '24 hours'
                """, Long.class);
        return n == null ? 0 : n;
    }

    // how long the oldest waiting job has been waiting = how far behind the workers are (0 if nothing waits)
    public double oldestPendingAgeSeconds() {
        Double age = jdbc.queryForObject("""
                SELECT COALESCE(EXTRACT(EPOCH FROM now() - min(created_at)), 0)::double precision
                FROM compute_job WHERE status = 'PENDING'
                """, Double.class);
        return age == null ? 0 : age;
    }

    private static String interval(Duration d) {
        return d.toMillis() + " milliseconds";
    }
}
