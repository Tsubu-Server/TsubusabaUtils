package net.tsubu.tsubusabautils.manager;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private boolean enabled;
    private String jdbcUrl;
    private String user;
    private String password;

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
        this.user = plugin.getConfig().getString("database.user");
        this.password = plugin.getConfig().getString("database.password");

        this.jdbcUrl = "jdbc:mariadb://" + host + ":" + port + "/" + db
                + "?autoReconnect=true&useSSL=false&allowPublicKeyRetrieval=true";

        try {
            Class.forName("org.mariadb.jdbc.Driver");
            try (Connection testConn = getConnection()) {
                plugin.getLogger().info("Database connected successfully.");
            }
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

    /**
     * 新しい接続を取得（使用後は必ず閉じること）
     */
    public Connection getConnection() throws SQLException {
        if (!enabled) {
            throw new SQLException("Database is not enabled");
        }
        return DriverManager.getConnection(jdbcUrl, user, password);
    }

    public void closeConnection() {
        plugin.getLogger().info("Database manager closed.");
    }

    /**
     * テーブル作成用のユーティリティ
     */
    public void createTable(String sql) {
        if (!enabled) return;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create table: " + e.getMessage());
        }
    }
}
