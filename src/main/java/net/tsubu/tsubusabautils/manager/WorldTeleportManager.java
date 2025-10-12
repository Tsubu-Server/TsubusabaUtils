package net.tsubu.tsubusabautils.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WorldTeleportManager implements Listener {

    private final Map<UUID, Map<String, LastLocation>> lastLocations = new HashMap<>();
    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;

    private static class LastLocation {
        private final Location location;
        private final long timestamp;

        public LastLocation(Location location) {
            this.location = location;
            this.timestamp = System.currentTimeMillis();
        }

        public Location getLocation() {
            return location;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    public WorldTeleportManager(JavaPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;

        Bukkit.getPluginManager().registerEvents(this, plugin);

        // データベースが有効ならテーブルを作成
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
            loadAllLocations();
        }
    }

    private void loadAllLocations() {
        if (!databaseManager.isEnabled()) return;
        String sql = "SELECT * FROM player_last_locations";

        try (Statement stmt = databaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String world = rs.getString("world");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                float yaw = rs.getFloat("yaw");
                float pitch = rs.getFloat("pitch");

                lastLocations.putIfAbsent(uuid, new HashMap<>());
                World bukkitWorld = Bukkit.getWorld(world);
                if (bukkitWorld != null) {
                    lastLocations.get(uuid).put(world,
                            new LastLocation(new Location(bukkitWorld, x, y, z, yaw, pitch))
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("座標のロードに失敗しました: " + e.getMessage());
        }
    }

    private void saveLocationToDatabase(Player player, String worldName, Location loc) {
        if (!databaseManager.isEnabled()) return;
        String sql = "REPLACE INTO player_last_locations (uuid, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setString(2, worldName);
            stmt.setDouble(3, loc.getX());
            stmt.setDouble(4, loc.getY());
            stmt.setDouble(5, loc.getZ());
            stmt.setFloat(6, loc.getYaw());
            stmt.setFloat(7, loc.getPitch());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("座標の保存に失敗しました: " + e.getMessage());
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        String fromWorld = from.getWorld().getName();

        lastLocations.putIfAbsent(player.getUniqueId(), new HashMap<>());
        lastLocations.get(player.getUniqueId()).put(fromWorld, new LastLocation(from));

        saveLocationToDatabase(player, fromWorld, from);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        lastLocations.putIfAbsent(event.getPlayer().getUniqueId(), new HashMap<>());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Map<String, LastLocation> map = lastLocations.get(player.getUniqueId());
        if (map != null) {
            for (Map.Entry<String, LastLocation> entry : map.entrySet()) {
                saveLocationToDatabase(player, entry.getKey(), entry.getValue().getLocation());
            }
        }
    }

    public Location getLastLocationByPrefix(Player player, String prefix) {
        Map<String, LastLocation> map = lastLocations.get(player.getUniqueId());
        if (map == null) return null;

        LastLocation last = null;
        for (Map.Entry<String, LastLocation> entry : map.entrySet()) {
            String worldName = entry.getKey();
            if (worldName.toLowerCase().startsWith(prefix.toLowerCase())) {
                if (last == null || entry.getValue().getTimestamp() > last.getTimestamp()) {
                    last = entry.getValue();
                }
            }
        }
        return last != null ? last.getLocation() : null;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }
}
