package net.tsubu.tsubusabautils.manager;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerCacheManager implements Listener {

    private final JavaPlugin plugin;
    private final DatabaseManager dbManager;

    public PlayerCacheManager(JavaPlugin plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
        initTable();
    }

    private void initTable() {
        dbManager.createTable(
                "CREATE TABLE IF NOT EXISTS player_cache (" +
                        "uuid VARCHAR(36) PRIMARY KEY," +
                        "name VARCHAR(16) NOT NULL," +
                        "last_seen BIGINT NOT NULL," +
                        "INDEX idx_name (name)," +
                        "INDEX idx_last_seen (last_seen)" +
                        ")"
        );
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        updatePlayerCache(p.getUniqueId(), p.getName(), System.currentTimeMillis());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        updatePlayerCache(p.getUniqueId(), p.getName(), System.currentTimeMillis());
    }

    public void updatePlayerCache(UUID uuid, String name, long lastSeen) {
        if (!dbManager.isEnabled()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO player_cache (uuid, name, last_seen) VALUES (?, ?, ?) " +
                                 "ON DUPLICATE KEY UPDATE name = ?, last_seen = ?")) {

                stmt.setString(1, uuid.toString());
                stmt.setString(2, name);
                stmt.setLong(3, lastSeen);
                stmt.setString(4, name);
                stmt.setLong(5, lastSeen);
                stmt.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to update player cache: " + e.getMessage());
            }
        });
    }

    public void getPlayersAsync(String searchQuery, int limit, int offset, PlayerListCallback callback) {
        if (!dbManager.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.onResult(new ArrayList<>(), 0));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<CachedPlayer> players = new ArrayList<>();
            int total = 0;

            try (Connection conn = dbManager.getConnection()) {
                String whereClause = "";
                if (searchQuery != null && !searchQuery.isEmpty()) {
                    whereClause = "WHERE name LIKE ? ";
                }

                try (PreparedStatement countStmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM player_cache " + whereClause)) {

                    if (!whereClause.isEmpty()) {
                        countStmt.setString(1, "%" + searchQuery + "%");
                    }
                    ResultSet rs = countStmt.executeQuery();
                    if (rs.next()) total = rs.getInt(1);
                }

                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT uuid, name, last_seen FROM player_cache " +
                                whereClause +
                                "ORDER BY last_seen DESC LIMIT ? OFFSET ?")) {

                    int paramIndex = 1;
                    if (!whereClause.isEmpty()) {
                        stmt.setString(paramIndex++, "%" + searchQuery + "%");
                    }
                    stmt.setInt(paramIndex++, limit);
                    stmt.setInt(paramIndex, offset);

                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        players.add(new CachedPlayer(
                                UUID.fromString(rs.getString("uuid")),
                                rs.getString("name"),
                                rs.getLong("last_seen")
                        ));
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to fetch players: " + e.getMessage());
            }

            int finalTotal = total;
            Bukkit.getScheduler().runTask(plugin, () -> callback.onResult(players, finalTotal));
        });
    }

    public void initializeCacheFromOfflinePlayers() {
        if (!dbManager.isEnabled()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int count = 0;
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op.getName() != null && op.hasPlayedBefore()) {
                    updatePlayerCache(op.getUniqueId(), op.getName(), op.getLastPlayed());
                    count++;
                }
            }
            int finalCount = count;
            plugin.getLogger().info("Player cache initialized with " + finalCount + " players!");
        });
    }

    public static class CachedPlayer {
        public final UUID uuid;
        public final String name;
        public final long lastSeen;

        public CachedPlayer(UUID uuid, String name, long lastSeen) {
            this.uuid = uuid;
            this.name = name;
            this.lastSeen = lastSeen;
        }

        public boolean isOnline() {
            return Bukkit.getPlayer(uuid) != null;
        }

        public OfflinePlayer getOfflinePlayer() {
            return Bukkit.getOfflinePlayer(uuid);
        }
    }

    public interface PlayerListCallback {
        void onResult(List<CachedPlayer> players, int totalCount);
    }
}