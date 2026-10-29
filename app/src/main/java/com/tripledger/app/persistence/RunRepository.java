package com.tripledger.app.persistence;

import com.tripledger.app.routing.RoutingUsage;
import com.tripledger.core.model.GeoPoint;
import com.tripledger.core.model.Segment;
import com.tripledger.core.model.SegmentLabel;
import com.tripledger.core.model.TripDistance;
import com.tripledger.core.model.TripStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computation runs and their segments.
 *
 * A run is written once and never changed. The trip just points at its latest run, so the
 * history of every bill stays intact - handy when a vendor disputes an old invoice.
 */
@Repository
public class RunRepository {

    private static final int BATCH_SIZE = 1_000;

    private static final String RUN_COLUMNS = """
            r.id, r.trip_id, r.input_hash, r.rule_version, r.status, r.total_mm, r.billable_km,
            r.route_requests, r.cache_hits, r.provider_calls, r.routing_unavailable,
            r.cleaning_json::text AS cleaning_json, r.anchors_json::text AS anchors_json, r.created_at
            """;

    private final JdbcTemplate jdbc;

    public RunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // run + all its segments + "trip now points here", all or nothing.
    // If an identical good run already exists, the unique index throws DuplicateKeyException and
    // nothing is written - the caller treats that as "someone else already did this".
    @Transactional
    public void save(String tenant, UUID runId, String inputHash, TripDistance result, RoutingUsage usage,
            String cleaningJson, String anchorsJson) {
        jdbc.update("""
                INSERT INTO computation_run (id, tenant_id, trip_id, input_hash, rule_version, status,
                    total_mm, billable_km, route_requests, cache_hits, provider_calls, routing_unavailable,
                    cleaning_json, anchors_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
                """,
                runId, tenant, result.tripId(), inputHash, result.ruleSetVersion(), result.status().name(),
                result.total().millimetres(), result.billableKm(), usage.requests(), usage.cacheHits(),
                usage.providerCalls(), usage.unavailable(), cleaningJson, anchorsJson);

        List<Segment> segments = result.segments();
        if (!segments.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO run_segment (run_id, seq, from_ts, to_ts, from_lat_e6, from_lon_e6,
                        to_lat_e6, to_lon_e6, distance_mm, rule)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, segments, BATCH_SIZE, (ps, s) -> {
                        ps.setObject(1, runId);
                        ps.setInt(2, s.seq());
                        Sql.setTimestamp(ps, 3, s.from().time());
                        Sql.setTimestamp(ps, 4, s.to().time());
                        ps.setLong(5, s.from().point().latE6());
                        ps.setLong(6, s.from().point().lonE6());
                        ps.setLong(7, s.to().point().latE6());
                        ps.setLong(8, s.to().point().lonE6());
                        ps.setLong(9, s.distance().millimetres());
                        ps.setString(10, s.label().name());
                    });
        }

        jdbc.update("""
                UPDATE trip SET status = ?, latest_run_id = ?, updated_at = now()
                WHERE tenant_id = ? AND trip_id = ?
                """, TripState.from(result.status()).name(), runId, tenant, result.tripId());
    }

    public Optional<RunRow> latest(String tenant, String tripId) {
        return jdbc.query("SELECT " + RUN_COLUMNS + """
                FROM computation_run r
                JOIN trip t ON t.latest_run_id = r.id
                WHERE t.tenant_id = ? AND t.trip_id = ?
                """, RunRepository::mapRun, tenant, tripId).stream().findFirst();
    }

    // the good run for exactly this evidence, if one was already saved (see the unique index)
    public Optional<RunRow> findComputed(String tenant, String tripId, String inputHash, String ruleVersion) {
        return jdbc.query("SELECT " + RUN_COLUMNS + """
                FROM computation_run r
                WHERE r.tenant_id = ? AND r.trip_id = ? AND r.input_hash = ? AND r.rule_version = ?
                  AND r.status = 'COMPUTED'
                """, RunRepository::mapRun, tenant, tripId, inputHash, ruleVersion).stream().findFirst();
    }

    public List<SegmentRow> segments(UUID runId) {
        return jdbc.query("""
                SELECT seq, from_ts, to_ts, from_lat_e6, from_lon_e6, to_lat_e6, to_lon_e6, distance_mm, rule
                FROM run_segment WHERE run_id = ? ORDER BY seq
                """, (rs, n) -> new SegmentRow(
                        rs.getInt("seq"),
                        Sql.instant(rs, "from_ts"),
                        new GeoPoint(rs.getLong("from_lat_e6"), rs.getLong("from_lon_e6")),
                        Sql.instant(rs, "to_ts"),
                        new GeoPoint(rs.getLong("to_lat_e6"), rs.getLong("to_lon_e6")),
                        rs.getLong("distance_mm"),
                        SegmentLabel.valueOf(rs.getString("rule"))),
                runId);
    }

    private static RunRow mapRun(ResultSet rs, int rowNum) throws SQLException {
        return new RunRow(
                rs.getObject("id", UUID.class),
                rs.getString("trip_id"),
                rs.getString("input_hash"),
                rs.getString("rule_version"),
                TripStatus.valueOf(rs.getString("status")),
                rs.getLong("total_mm"),
                rs.getBigDecimal("billable_km"),
                rs.getInt("route_requests"),
                rs.getInt("cache_hits"),
                rs.getInt("provider_calls"),
                rs.getInt("routing_unavailable"),
                rs.getString("cleaning_json"),
                rs.getString("anchors_json"),
                Sql.instant(rs, "created_at"));
    }
}
