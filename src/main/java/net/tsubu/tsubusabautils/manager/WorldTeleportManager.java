package net.tsubu.tsubusabautils.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;

public class WorldTeleportManager implements Listener {

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;

    public WorldTeleportManager(JavaPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;

        Bukkit.getPluginManager().registerEvents(this, plugin);

        if (databaseManager.isEnabled()) {
            String sql = """
                CREATE TABLE IF NOT EXISTS player_last_locations (
                    uuid CHAR(36) NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    yaw FLOAT NOT NULL,
                    pitch FLOAT NOT NULL,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (uuid, world)
                );
            """;
            databaseManager.createTable(sql);
        }
    }

    /**
     * DBから最後の座標を取得（prefixワールドに対応）
     */
    public Location getLastLocationByPrefix(Player player, String prefix) {
        if (!databaseManager.isEnabled()) return null;

        String sql = """
        SELECT * FROM player_last_locations
        WHERE uuid = ?
        AND world LIKE ?
        AND world NOT LIKE ?
        ORDER BY updated_at DESC
        LIMIT 1
    """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setString(2, prefix + "%");
            stmt.setString(3, "%_the_end%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                World world = Bukkit.getWorld(rs.getString("world"));
                if (world == null) return null;
                return new Location(
                        world,
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("座標取得に失敗: " + e.getMessage());
        }
        return null;
    }

    /**
     * 現在位置をDBに保存
     */
    public void saveCurrentLocation(Player player) {
        if (!databaseManager.isEnabled()) return;

        Location loc = player.getLocation();
        saveLocationToDatabase(player, loc.getWorld().getName(), loc);
    }

    /**
     * 指定座標をDBに保存
     */
    public void saveLocationToDatabase(Player player, String worldName, Location loc) {
        if (!databaseManager.isEnabled()) return;

        String sql = "REPLACE INTO player_last_locations (uuid, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setString(2, worldName);
            stmt.setDouble(3, loc.getX());
            stmt.setDouble(4, loc.getY());
            stmt.setDouble(5, loc.getZ());
            stmt.setFloat(6, loc.getYaw());
            stmt.setFloat(7, loc.getPitch());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("座標保存に失敗: " + e.getMessage());
        }
    }

    /**
     * ワールド間移動時に現在座標をDBに保存
     */
    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (!from.getWorld().equals(to.getWorld())) {
            if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) return;
            if (event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN
                    && from.getWorld().getName().equalsIgnoreCase("resource_the_end")
                    && to.getWorld().getName().equalsIgnoreCase("world")) return;
            saveLocationToDatabase(player, from.getWorld().getName(), from);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        saveCurrentLocation(event.getPlayer());
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }
}
