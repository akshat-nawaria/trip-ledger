package com.tripledger.app.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// signed corrections to already-billed trips. Insert-only: an adjustment is never edited or deleted.
@Repository
public class AdjustmentRepository {

    public record Adjustment(UUID id, String invoiceId, BigDecimal deltaKm, long deltaMm, UUID fromRunId,
            UUID toRunId, Instant createdAt) {
    }

    // everything adjusted so far, and the run the last adjustment moved the trip to (null if none yet)
    public record Totals(BigDecimal deltaKm, long deltaMm, UUID lastRunId) {
    }

    private final JdbcTemplate jdbc;

    public AdjustmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, String tenant, String tripId, String invoiceId, BigDecimal deltaKm, long deltaMm,
            UUID fromRunId, UUID toRunId) {
        jdbc.update("""
                INSERT INTO invoice_adjustment (id, tenant_id, trip_id, invoice_id, delta_km, delta_mm, from_run_id, to_run_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, tenant, tripId, invoiceId, deltaKm, deltaMm, fromRunId, toRunId);
    }

    public List<Adjustment> list(String tenant, String tripId) {
        return jdbc.query("""
                SELECT id, invoice_id, delta_km, delta_mm, from_run_id, to_run_id, created_at
                FROM invoice_adjustment WHERE tenant_id = ? AND trip_id = ?
                ORDER BY created_at, id
                """, (rs, n) -> new Adjustment(
                        rs.getObject("id", UUID.class),
                        rs.getString("invoice_id"),
                        rs.getBigDecimal("delta_km"),
                        rs.getLong("delta_mm"),
                        rs.getObject("from_run_id", UUID.class),
                        rs.getObject("to_run_id", UUID.class),
                        Sql.instant(rs, "created_at")),
                tenant, tripId);
    }

    public Totals totals(String tenant, String tripId) {
        List<Adjustment> all = list(tenant, tripId);
        BigDecimal km = BigDecimal.ZERO;
        long mm = 0;
        for (Adjustment a : all) {
            km = km.add(a.deltaKm());
            mm += a.deltaMm();
        }
        UUID last = all.isEmpty() ? null : all.get(all.size() - 1).toRunId();
        return new Totals(km, mm, last);
    }
}
