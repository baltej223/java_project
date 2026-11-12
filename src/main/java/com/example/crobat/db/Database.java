package com.example.crobat.db;

import com.example.crobat.config.Config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Opens plain JDBC connections. No pool; each operation closes its own. */
public class Database {
    private final String url;
    private final String user;
    private final String password;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            throw new ExceptionInInitializerError("MySQL driver missing: " + ex.getMessage());
        }
    }

    public Database(Config config) {
        this.url = config.getMysqlUrl();
        this.user = config.getMysqlUser();
        this.password = config.getMysqlPassword();
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public void ping() {
        try (Connection connection = getConnection()) {
            if (!connection.isValid(5)) {
                throw new DatabaseException("MySQL connection is not valid");
            }
        } catch (SQLException ex) {
            throw new DatabaseException("cannot connect to MySQL", ex);
        }
    }
}
