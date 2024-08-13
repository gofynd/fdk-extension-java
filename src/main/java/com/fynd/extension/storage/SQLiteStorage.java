package com.fynd.extension.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.Map;
import org.springframework.util.StringUtils;

public class SQLiteStorage implements BaseStorage {

    private final String dbUrl = "jdbc:sqlite:session_storage.db";
    private final String prefixKey;
    private Thread ttlCheckerThread;

    public SQLiteStorage(String prefixKey) throws ClassNotFoundException {
        this.prefixKey = StringUtils.hasText(prefixKey) ? prefixKey + ":" : "";
        Class.forName("org.sqlite.JDBC");
        initDatabase();
        setupTTLChecker();
    }

    private void initDatabase() {

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            if (conn != null) {
                String createTable = "CREATE TABLE IF NOT EXISTS storage (key TEXT PRIMARY KEY, value TEXT, ttl INTEGER)";
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(createTable);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error initializing SQLite database", e);
        }
    }

    private void setupTTLChecker() {
        ttlCheckerThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try (Connection conn = DriverManager.getConnection(dbUrl);
                     PreparedStatement pstmt = conn.prepareStatement("DELETE FROM storage WHERE ttl < ? AND ttl IS NOT NULL")) {
                    pstmt.setLong(1, System.currentTimeMillis() / 1000);
                    pstmt.executeUpdate();
                    Thread.sleep(10000);
                } catch (SQLException | InterruptedException e) {
                    throw new RuntimeException("Error during TTL check", e);
                }
            }
        });
        ttlCheckerThread.setDaemon(true);
        ttlCheckerThread.start();
    }

    @Override
    public String get(String key) {
        String query = "SELECT value FROM storage WHERE key = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, prefixKey + key);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("value");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error getting value from SQLite", e);
        }
        return null;
    }

    @Override
    public String set(String key, String value) {
        String query = "INSERT INTO storage (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, prefixKey + key);
            pstmt.setString(2, value);
            pstmt.executeUpdate();
            return value;
        } catch (SQLException e) {
            throw new RuntimeException("Error setting value in SQLite", e);
        }
    }

    @Override
    public Long del(String key) {
        String query = "DELETE FROM storage WHERE key = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, prefixKey + key);
            int affectedRows = pstmt.executeUpdate();
            return (long) affectedRows;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting value from SQLite", e);
        }
    }

    @Override
    public String setex(String key, int ttl, String value) {
        String query = "INSERT INTO storage (key, value, ttl) VALUES (?, ?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value, ttl = excluded.ttl";
        long expiresAt = System.currentTimeMillis() / 1000 + ttl;
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, prefixKey + key);
            pstmt.setString(2, value);
            pstmt.setLong(3, expiresAt);
            pstmt.executeUpdate();
            return value;
        } catch (SQLException e) {
            throw new RuntimeException("Error setting value with TTL in SQLite", e);
        }
    }

    @Override
    public String hget(String key, String hashKey) {
        throw new UnsupportedOperationException("hget not implemented for SQLiteStorage");
    }

    @Override
    public Long hset(String key, String hashKey, String value) {
        throw new UnsupportedOperationException("hset not implemented for SQLiteStorage");
    }

    @Override
    public Map<String, Object> hgetall(String key) {
        throw new UnsupportedOperationException("hgetall not implemented for SQLiteStorage");
    }

    public void stopTTLChecker() {
        if (ttlCheckerThread != null && ttlCheckerThread.isAlive()) {
            ttlCheckerThread.interrupt();
        }
    }
}
