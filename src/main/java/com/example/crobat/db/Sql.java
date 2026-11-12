package com.example.crobat.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

final class Sql {
    private Sql() {
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    static String string(ResultSet rs, String column) throws SQLException {
        return rs.getString(column);
    }

    static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static void setInstant(PreparedStatement ps, int index, Instant instant)
            throws SQLException {
        if (instant == null) {
            ps.setNull(index, java.sql.Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, Timestamp.from(instant));
        }
    }

    static void setString(PreparedStatement ps, int index, String value)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    static String placeholders(int count) {
        StringBuilder out = new StringBuilder(count * 2);
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append('?');
        }
        return out.toString();
    }

    static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
