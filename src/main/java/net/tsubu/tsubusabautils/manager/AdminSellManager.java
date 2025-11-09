package net.tsubu.tsubusabautils.manager;

import net.milkbowl.vault.economy.Economy;
import net.tsubu.tsubusabautils.TsubusabaUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

public class AdminSellManager {

    private final TsubusabaUtils plugin;
    private final Economy economy;
    private final Map<String, AdminSellCategory> categories = new HashMap<>();

    public AdminSellManager(TsubusabaUtils plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public void loadAllCategories() {
        File folder = plugin.getDataFolder();
        File[] files = folder.listFiles((dir, name) -> name.startsWith("adminsell") && name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String base = file.getName().replace("adminsell", "").replace(".yml", "");
            String category = base.startsWith("_") ? base.substring(1) : "default";
            loadCategory(category);
        }
        plugin.getLogger().info("Loaded " + categories.size() + " AdminSell categories.");
    }

    public AdminSellCategory loadCategory(String name) {
        AdminSellCategory cat = new AdminSellCategory(plugin, economy, name);
        categories.put(name.toLowerCase(Locale.ROOT), cat);
        return cat;
    }

    public AdminSellCategory getCategory(String name) {
        return categories.computeIfAbsent(name.toLowerCase(Locale.ROOT), n -> loadCategory(n));
    }

    public void reloadAll() {
        categories.clear();
        loadAllCategories();
    }

    public void openCategory(Player player, String name) {
        getCategory(name).open(player);
    }

    public void registerAllListeners() {
        for (AdminSellCategory category : categories.values()) {
            Bukkit.getPluginManager().registerEvents(category, plugin);
        }
    }
}
