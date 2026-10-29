package com.tripledger.app.persistence;

import com.tripledger.core.model.GeoPoint;
import com.tripledger.core.model.TripType;
import java.sql.ResultSet;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TripRepository {

    private static final String COLUMNS = """
            trip_id, trip_type, window_start, window_end, office_lat_e6, office_lon_e6,
            status, latest_run_id, updated_at
            """;

    private final JdbcTemplate jdbc;

    public TripRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // creates the trip on its first upload. Does nothing if it already exists (the caller then
    // checks the stored details match). Returns true if this call created it.
    public boolean insertIfAbsent(String tenant, String tripId, TripType type, Instant windowStart,
            Instant windowEnd, GeoPoint office) {
        int rows = jdbc.update("""
                INSERT INTO trip (tenant_id, trip_id, trip_type, window_start, window_end,
                                  office_lat_e6, office_lon_e6, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'RECEIVED')
                ON CONFLICT (tenant_id, trip_id) DO NOTHING
                """,
                tenant, tripId, type.name(), Sql.utc(windowStart), Sql.utc(windowEnd), office.latE6(), office.lonE6());
        return rows == 1;
    }

    public Optional<TripRow> find(String tenant, String tripId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM trip WHERE tenant_id = ? AND trip_id = ?",
                TripRepository::map, tenant, tripId).stream().findFirst();
    }

    // same as find, but holds a row lock until the transaction ends. Two uploads for the same trip
    // then go one after the other, which keeps the Idempotency-Key check race-free.
    public Optional<TripRow> lock(String tenant, String tripId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM trip WHERE tenant_id = ? AND trip_id = ? FOR UPDATE",
                TripRepository::map, tenant, tripId).stream().findFirst();
    }

    public void markReceived(String tenant, String tripId) {
        jdbc.update("UPDATE trip SET status = 'RECEIVED', updated_at = now() WHERE tenant_id = ? AND trip_id = ?",
                tenant, tripId);
    }

    public void markComputed(String tenant, String tripId, TripState status, UUID runId) {
        jdbc.update("""
                UPDATE trip SET status = ?, latest_run_id = ?, updated_at = now()
                WHERE tenant_id = ? AND trip_id = ?
                """, status.name(), runId, tenant, tripId);
    }

    // the trip's billing info, locked until the transaction ends (so billing and adjustments of the
    // same trip can never interleave)
    public Optional<TripBilling> lockBilling(String tenant, String tripId) {
        return jdbc.query("""
                SELECT invoice_id, billed_run_id, billed_km, billed_at FROM trip
                WHERE tenant_id = ? AND trip_id = ? FOR UPDATE
                """, (rs, n) -> new TripBilling(rs.getString("invoice_id"), rs.getObject("billed_run_id", UUID.class),
                        rs.getBigDecimal("billed_km"), Sql.instant(rs, "billed_at")),
                tenant, tripId).stream().findFirst();
    }

    // same as lockBilling, without the lock (for reads)
    public TripBilling billing(String tenant, String tripId) {
        return jdbc.query("""
                SELECT invoice_id, billed_run_id, billed_km, billed_at FROM trip
                WHERE tenant_id = ? AND trip_id = ?
                """, (rs, n) -> new TripBilling(rs.getString("invoice_id"), rs.getObject("billed_run_id", UUID.class),
                        rs.getBigDecimal("billed_km"), Sql.instant(rs, "billed_at")),
                tenant, tripId).stream().findFirst().orElse(TripBilling.NOT_BILLED);
    }

    // only ever sets billing once: "invoice_id IS NULL" makes a second attempt a no-op (returns false)
    public boolean markBilled(String tenant, String tripId, String invoiceId, UUID runId, BigDecimal billedKm) {
        return jdbc.update("""
                UPDATE trip SET invoice_id = ?, billed_run_id = ?, billed_km = ?, billed_at = now(), updated_at = now()
                WHERE tenant_id = ? AND trip_id = ? AND invoice_id IS NULL
                """, invoiceId, runId, billedKm, tenant, tripId) == 1;
    }

    // newest first; status null = all trips
    public List<TripRow> list(String tenant, TripState status, int limit) {
        if (status == null) {
            return jdbc.query("SELECT " + COLUMNS + " FROM trip WHERE tenant_id = ? ORDER BY updated_at DESC, trip_id LIMIT ?",
                    TripRepository::map, tenant, limit);
        }
        return jdbc.query("SELECT " + COLUMNS
                        + " FROM trip WHERE tenant_id = ? AND status = ? ORDER BY updated_at DESC, trip_id LIMIT ?",
                TripRepository::map, tenant, status.name(), limit);
    }

    private static TripRow map(ResultSet rs, int rowNum) throws SQLException {
        return new TripRow(
                rs.getString("trip_id"),
                TripType.valueOf(rs.getString("trip_type")),
                Sql.instant(rs, "window_start"),
                Sql.instant(rs, "window_end"),
                new GeoPoint(rs.getLong("office_lat_e6"), rs.getLong("office_lon_e6")),
                TripState.valueOf(rs.getString("status")),
                rs.getObject("latest_run_id", UUID.class),
                Sql.instant(rs, "updated_at"));
    }
}
