package com.tripledger.app.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

// small JDBC helpers so the repositories don't repeat the same null / timestamp dance everywhere.
// Timestamps always go in and out as UTC OffsetDateTime - the type the Postgres driver handles natively.
final class Sql {

    private Sql() {
    }

    static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    static void setTimestamp(PreparedStatement ps, int index, Instant instant) throws SQLException {
        if (instant == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setObject(index, utc(instant));
        }
    }

    static void setLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    static int countInserted(int[][] batchCounts) {
        int inserted = 0;
        for (int[] batch : batchCounts) {
            for (int count : batch) {
                inserted += count; // ON CONFLICT DO NOTHING reports 0 for a duplicate, 1 for a new row
            }
        }
        return inserted;
    }
}
