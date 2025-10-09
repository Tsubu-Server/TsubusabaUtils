package net.tsubu.tsubusabautils.manager;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private Connection connection;
    private boolean enabled;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("database.enabled", false);
        if (enabled) {
            initConnection();
        }
    }

    private void initConnection() {
        String host = plugin.getConfig().getString("database.host");
        int port = plugin.getConfig().getInt("database.port");
        String db = plugin.getConfig().getString("database.database");
        String user = plugin.getConfig().getString("database.user");
        String pass = plugin.getConfig().getString("database.password");

        try {
            Class.forName("org.mariadb.jdbc.Driver");
            connection = DriverManager.getConnection(
                    "jdbc:mariadb://" + host + ":" + port + "/" + db, user, pass
            );
            plugin.getLogger().info("Database connected successfully.");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("MariaDB driver not found.");
            enabled = false;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to database: " + e.getMessage());
            enabled = false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Connection getConnection() {
        return connection;
    }

    public void closeConnection() {
        try {
            if (connection != null) connection.close();
        } catch (SQLException ignored) {}
    }

    /**
     * テーブル作成用のユーティリティ
     */
    public void createTable(String sql) {
        if (!enabled) return;
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create table: " + e.getMessage());
        }
    }
}
