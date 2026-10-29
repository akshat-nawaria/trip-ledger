package com.tripledger.app.persistence;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TenantConfigRepository {

    private final JdbcTemplate jdbc;

    public TenantConfigRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TenantSettings> latest(String tenant) {
        return jdbc.query("""
                SELECT version, no_show_anchor_enabled, max_accuracy_m, max_speed_kmph, office_radius_m, max_route_km
                FROM tenant_config WHERE tenant_id = ?
                ORDER BY version DESC LIMIT 1
                """, (rs, n) -> new TenantSettings(
                        rs.getInt("version"),
                        rs.getBoolean("no_show_anchor_enabled"),
                        rs.getInt("max_accuracy_m"),
                        rs.getDouble("max_speed_kmph"),
                        rs.getInt("office_radius_m"),
                        rs.getInt("max_route_km")),
                tenant).stream().findFirst();
    }

    // saves the settings as the next version and returns that version number. If two saves race,
    // both pick the same number and the primary key lets only one through (DuplicateKeyException).
    public int insertNextVersion(String tenant, TenantSettings s, String changedBy) {
        Integer version = jdbc.queryForObject("""
                INSERT INTO tenant_config (tenant_id, version, no_show_anchor_enabled, max_accuracy_m,
                                           max_speed_kmph, office_radius_m, max_route_km, changed_by)
                SELECT CAST(? AS text), COALESCE(MAX(version), 0) + 1, CAST(? AS boolean), CAST(? AS int),
                       CAST(? AS double precision), CAST(? AS int), CAST(? AS int), CAST(? AS text)
                FROM tenant_config WHERE tenant_id = ?
                RETURNING version
                """, Integer.class,
                tenant, s.noShowAnchorEnabled(), s.maxAccuracyMetres(), s.maxSpeedKmph(),
                s.officeRadiusMetres(), s.maxRouteKm(), changedBy, tenant);
        return version == null ? 0 : version;
    }
}
