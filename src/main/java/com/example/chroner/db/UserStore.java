package com.example.chroner.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Login users. The tenant is part of the key, so usernames can repeat. */
public class UserStore {
    public static class User {
        private final String tenantId;
        private final String username;
        private final String passwordHash;

        public User(String tenantId, String username, String passwordHash) {
            this.tenantId = tenantId;
            this.username = username;
            this.passwordHash = passwordHash;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getUsername() {
            return username;
        }

        public String getPasswordHash() {
            return passwordHash;
        }
    }

    private final Database database;

    public UserStore(Database database) {
        this.database = database;
    }

    public User find(String tenantId, String username) {
        String sql = "SELECT tenant_id, username, password_hash "
                + "FROM users WHERE tenant_id = ? AND username = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new User(
                        rs.getString("tenant_id"),
                        rs.getString("username"),
                        rs.getString("password_hash"));
            }
        } catch (SQLException ex) {
            throw new DatabaseException("find user", ex);
        }
    }
}
