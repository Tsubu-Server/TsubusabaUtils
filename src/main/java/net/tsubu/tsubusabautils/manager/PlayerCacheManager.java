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
import java.util.*;

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
                        "skin_url TEXT," +
                        "INDEX idx_name (name)," +
                        "INDEX idx_last_seen (last_seen)" +
                        ")"
        );
    }

    private String getSkinUrl(Player player) {
        try {
            var profile = player.getPlayerProfile();
            var textures = profile.getTextures();
            var skin = textures.getSkin();
            return (skin != null) ? skin.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public void updatePlayerCache(UUID uuid, String name, long lastSeen, String skinUrl) {
        if (!dbManager.isEnabled()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO player_cache (uuid, name, last_seen, skin_url) VALUES (?, ?, ?, ?) " +
                                 "ON DUPLICATE KEY UPDATE name = ?, last_seen = ?, skin_url = IFNULL(?, skin_url)"
                 )) {

                stmt.setString(1, uuid.toString());
                stmt.setString(2, name);
                stmt.setLong(3, lastSeen);
                stmt.setString(4, skinUrl);
                stmt.setString(5, name);
                stmt.setLong(6, lastSeen);
                stmt.setString(7, skinUrl);
                stmt.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to update player cache: " + e.getMessage());
            }
        });
    }

    public void getPlayersAsync(String searchQuery, int limit, int offset, CachedPlayer.PlayerListCallback callback) {
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
                        "SELECT uuid, name, last_seen, skin_url FROM player_cache " +
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
                                rs.getLong("last_seen"),
                                rs.getString("skin_url")
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
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op.getName() == null || !op.hasPlayedBefore()) continue;

                String skinUrl = null;
                try {
                    var profile = op.getPlayerProfile();
                    var textures = profile.getTextures();
                    var skin = textures.getSkin();
                    if (skin != null) skinUrl = skin.toString();
                } catch (Exception ignored) {}
                updatePlayerCache(op.getUniqueId(), op.getName(), op.getLastPlayed(), skinUrl);
            }

            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getLogger().info("Player cache initialized with offline players")
            );
        });
    }

    public static class CachedPlayer {
        public final UUID uuid;
        public final String name;
        public final long lastSeen;
        public final String skinUrl;
        private com.destroystokyo.paper.profile.PlayerProfile profile;

        public CachedPlayer(UUID uuid, String name, long lastSeen, String skinUrl) {
            this.uuid = uuid;
            this.name = name;
            this.lastSeen = lastSeen;
            this.skinUrl = skinUrl;
        }

        public boolean isOnline() {
            return Bukkit.getPlayer(uuid) != null;
        }

        public OfflinePlayer getOfflinePlayer() {
            return Bukkit.getOfflinePlayer(uuid);
        }

        public com.destroystokyo.paper.profile.PlayerProfile getProfile() {
            if (profile != null) return profile;

            profile = Bukkit.createProfile(uuid, name);

            if (skinUrl != null && !skinUrl.isEmpty()) {
                try {
                    profile.getTextures().setSkin(new java.net.URL(skinUrl));
                    return profile;
                } catch (java.net.MalformedURLException ignored) {}
            }

            var plugin = Bukkit.getPluginManager().getPlugin("TsubusabaUtils");
            if (plugin != null) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        com.destroystokyo.paper.profile.PlayerProfile fetched = Bukkit.createProfile(uuid, name);
                        fetched.complete(true);
                        this.profile = fetched;
                    } catch (Exception e) {
                        Bukkit.getLogger().warning("Failed to fetch skin for " + name + ": " + e.getMessage());
                    }
                });
            } else {
                Bukkit.getLogger().warning("Plugin TsubusabaUtils not found - cannot async fetch player profile for " + name);
            }

            return profile;
        }

        public void setProfile(com.destroystokyo.paper.profile.PlayerProfile profile) {
            this.profile = profile;
        }

        public interface PlayerListCallback {
            void onResult(List<CachedPlayer> players, int totalCount);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        String skinUrl = getSkinUrl(p);
        updatePlayerCache(p.getUniqueId(), p.getName(), System.currentTimeMillis(), skinUrl);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        updatePlayerCache(p.getUniqueId(), p.getName(), System.currentTimeMillis(), getSkinUrl(p));
    }
}
