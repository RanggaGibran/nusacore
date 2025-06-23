package id.nusacore.database;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CryptoHistoryDatabase {
    private static final String DB_URL = "jdbc:sqlite:plugins/NusaTown/crypto_history.db";
    private static final String TABLE_NAME = "crypto_price_history";

    public CryptoHistoryDatabase() {
        initializeDatabase();
    }

    private void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "crypto_id TEXT NOT NULL," +
                    "price REAL NOT NULL," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ")";
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void insertPrice(String cryptoId, double price) {
        String sql = "INSERT INTO " + TABLE_NAME + " (crypto_id, price, timestamp) VALUES (?, ?, datetime('now'))";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, cryptoId);
            pstmt.setDouble(2, price);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Double> getPriceHistory(String cryptoId, int limit) {
        List<Double> history = new ArrayList<>();
        String sql = "SELECT price FROM " + TABLE_NAME + " WHERE crypto_id = ? ORDER BY id DESC LIMIT ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, cryptoId);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                history.add(0, rs.getDouble("price")); // Insert at 0 to reverse order
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return history;
    }

    // Optional: migrate old YAML history to SQLite
    public void migrateFromYaml(String cryptoId, List<Double> oldHistory) {
        for (Double price : oldHistory) {
            insertPrice(cryptoId, price);
        }
    }
}
