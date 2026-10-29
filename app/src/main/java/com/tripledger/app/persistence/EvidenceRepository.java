package com.tripledger.app.persistence;

import com.tripledger.core.model.GeoPoint;
import com.tripledger.core.model.Milestone;
import com.tripledger.core.model.MilestoneType;
import com.tripledger.core.model.Ping;
import com.tripledger.core.model.PlannedStop;
import com.tripledger.core.model.RiderType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Raw evidence: pings, milestones, planned stops. Append-only.
 *
 * Every insert is "ON CONFLICT DO NOTHING", so sending the same ping twice (retries, overlapping
 * batch uploads) just doesn't add a row. The insert methods return how many rows were actually new,
 * which is how the API can tell a device "322 received, 0 new".
 */
@Repository
public class EvidenceRepository {

    private static final int BATCH_SIZE = 1_000;

    private final JdbcTemplate jdbc;

    public EvidenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int insertPings(String tenant, String tripId, List<Ping> pings) {
        if (pings.isEmpty()) {
            return 0;
        }
        int[][] counts = jdbc.batchUpdate("""
                INSERT INTO ping (tenant_id, trip_id, device_ts, lat_e6, lon_e6, accuracy_m, device_seq)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, trip_id, device_ts, lat_e6, lon_e6) DO NOTHING
                """, pings, BATCH_SIZE, (ps, p) -> {
                    ps.setString(1, tenant);
                    ps.setString(2, tripId);
                    Sql.setTimestamp(ps, 3, p.timestamp());
                    ps.setLong(4, p.point().latE6());
                    ps.setLong(5, p.point().lonE6());
                    ps.setInt(6, p.accuracyMetres());
                    ps.setLong(7, p.deviceSeq());
                });
        return Sql.countInserted(counts);
    }

    public int insertMilestones(String tenant, String tripId, List<Milestone> milestones) {
        if (milestones.isEmpty()) {
            return 0;
        }
        int[][] counts = jdbc.batchUpdate("""
                INSERT INTO milestone (tenant_id, trip_id, dedupe_key, employee_ref, type, lat_e6, lon_e6, event_ts)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, trip_id, dedupe_key) DO NOTHING
                """, milestones, BATCH_SIZE, (ps, m) -> {
                    ps.setString(1, tenant);
                    ps.setString(2, tripId);
                    ps.setString(3, dedupeKey(m.type().name(), m.employeeRef(), m.point(), m.time()));
                    ps.setString(4, m.employeeRef());
                    ps.setString(5, m.type().name());
                    Sql.setLong(ps, 6, m.point() == null ? null : m.point().latE6());
                    Sql.setLong(ps, 7, m.point() == null ? null : m.point().lonE6());
                    Sql.setTimestamp(ps, 8, m.time());
                });
        return Sql.countInserted(counts);
    }

    public int insertPlannedStops(String tenant, String tripId, List<PlannedStop> stops) {
        if (stops.isEmpty()) {
            return 0;
        }
        int[][] counts = jdbc.batchUpdate("""
                INSERT INTO planned_stop (tenant_id, trip_id, dedupe_key, employee_ref, rider_type, lat_e6, lon_e6, planned_ts)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, trip_id, dedupe_key) DO NOTHING
                """, stops, BATCH_SIZE, (ps, s) -> {
                    ps.setString(1, tenant);
                    ps.setString(2, tripId);
                    ps.setString(3, dedupeKey(s.riderType().name(), s.employeeRef(), s.point(), s.time()));
                    ps.setString(4, s.employeeRef());
                    ps.setString(5, s.riderType().name());
                    Sql.setLong(ps, 6, s.point() == null ? null : s.point().latE6());
                    Sql.setLong(ps, 7, s.point() == null ? null : s.point().lonE6());
                    Sql.setTimestamp(ps, 8, s.time());
                });
        return Sql.countInserted(counts);
    }

    // order doesn't matter to the engine (it sorts everything itself), but a fixed order keeps
    // debugging and test output stable
    public List<Ping> pings(String tenant, String tripId) {
        return jdbc.query("""
                SELECT device_ts, lat_e6, lon_e6, accuracy_m, device_seq FROM ping
                WHERE tenant_id = ? AND trip_id = ?
                ORDER BY device_ts, device_seq, lat_e6, lon_e6
                """, (rs, n) -> new Ping(
                        Sql.instant(rs, "device_ts"),
                        new GeoPoint(rs.getLong("lat_e6"), rs.getLong("lon_e6")),
                        rs.getInt("accuracy_m"),
                        rs.getLong("device_seq")),
                tenant, tripId);
    }

    public List<Milestone> milestones(String tenant, String tripId) {
        return jdbc.query("""
                SELECT employee_ref, type, lat_e6, lon_e6, event_ts FROM milestone
                WHERE tenant_id = ? AND trip_id = ? ORDER BY id
                """, (rs, n) -> new Milestone(
                        rs.getString("employee_ref"),
                        MilestoneType.valueOf(rs.getString("type")),
                        point(rs),
                        Sql.instant(rs, "event_ts")),
                tenant, tripId);
    }

    public List<PlannedStop> plannedStops(String tenant, String tripId) {
        return jdbc.query("""
                SELECT employee_ref, rider_type, lat_e6, lon_e6, planned_ts FROM planned_stop
                WHERE tenant_id = ? AND trip_id = ? ORDER BY id
                """, (rs, n) -> new PlannedStop(
                        rs.getString("employee_ref"),
                        RiderType.valueOf(rs.getString("rider_type")),
                        point(rs),
                        Sql.instant(rs, "planned_ts")),
                tenant, tripId);
    }

    // a point is only usable if both halves are there
    private static GeoPoint point(ResultSet rs) throws SQLException {
        Long lat = Sql.nullableLong(rs, "lat_e6");
        Long lon = Sql.nullableLong(rs, "lon_e6");
        return lat == null || lon == null ? null : new GeoPoint(lat, lon);
    }

    // milestones and stops can have missing fields, so a plain multi-column unique key won't work
    // (NULLs never clash in SQL). Instead every field goes into one string, with "-" for missing.
    static String dedupeKey(String type, String employeeRef, GeoPoint point, Instant time) {
        return type + "|" + (employeeRef == null ? "-" : employeeRef) + "|"
                + (point == null ? "-" : point.latE6() + "," + point.lonE6()) + "|"
                + (time == null ? "-" : time.toString());
    }
}
