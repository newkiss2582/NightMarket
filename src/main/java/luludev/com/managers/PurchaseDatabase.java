package luludev.com.managers;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;

public class PurchaseDatabase {

    private final Plugin plugin;
    private Connection connection;

    public PurchaseDatabase(Plugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "nightmarket.db");
            if (!dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);

            try (Statement st = connection.createStatement()) {
                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS purchases (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                "uuid TEXT NOT NULL," +
                                "name TEXT NOT NULL," +
                                "pool TEXT NOT NULL," +
                                "item_name TEXT NOT NULL," +
                                "price REAL NOT NULL," +
                                "date TEXT NOT NULL," +       // yyyy-MM-dd
                                "created_at TEXT NOT NULL" +   // ISO datetime
                                ");"
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to init database: " + e.getMessage());
        }
    }

    public boolean hasPurchasedToday(String uuid, String pool) {
        String today = LocalDate.now().toString();
        String sql = "SELECT 1 FROM purchases WHERE uuid = ? AND pool = ? AND date = ? LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, pool);
            ps.setString(3, today);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("hasPurchasedToday error: " + e.getMessage());
            return false;
        }
    }

    public void logPurchase(String uuid, String name, String pool,
                            String itemName, double price) {
        String today = LocalDate.now().toString();
        String now = java.time.ZonedDateTime.now().toString();
        String sql = "INSERT INTO purchases (uuid, name, pool, item_name, price, date, created_at)" +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, name);
            ps.setString(3, pool);
            ps.setString(4, itemName);
            ps.setDouble(5, price);
            ps.setString(6, today);
            ps.setString(7, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("logPurchase error: " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed())
                connection.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Error closing DB: " + e.getMessage());
        }
    }
}
