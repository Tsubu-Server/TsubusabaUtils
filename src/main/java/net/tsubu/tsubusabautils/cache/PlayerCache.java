package net.tsubu.tsubusabautils.cache;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerCache {
    private static final List<OfflinePlayer> playerList = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicBoolean loading = new AtomicBoolean(false);

    public static void reload(Plugin plugin) {
        if (loading.getAndSet(true)) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer[] all = Bukkit.getOfflinePlayers();
            List<OfflinePlayer> tmp = new ArrayList<>(Arrays.asList(all));
            tmp.removeIf(op -> op.getName() == null);
            tmp.sort(Comparator.comparing(op -> op.getName().toLowerCase(Locale.ROOT)));
            Bukkit.getScheduler().runTask(plugin, () -> {
                playerList.clear();
                playerList.addAll(tmp);
                loading.set(false);
            });
        });
    }

    public static List<OfflinePlayer> getAll() {
        synchronized (playerList) {
            return new ArrayList<>(playerList);
        }
    }
}