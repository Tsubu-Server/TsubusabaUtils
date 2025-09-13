package net.tsubu.tsubusabautils.manager;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import net.tsubu.tsubusabautils.TsubusabaUtils;
import net.milkbowl.vault.economy.Economy;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class HomeManager {

    private final Essentials essentials;
    private final JavaPlugin plugin;
    private final LuckPerms luckPerms;
    private final Map<String, Double> groupPrices;
    private final Map<String, String> groupDisplayNames;
    private final Map<String, String> luckPermsGroups;

    public HomeManager(Essentials essentials, JavaPlugin plugin, LuckPerms luckPerms) {
        this.essentials = essentials;
        this.plugin = plugin;
        this.luckPerms = luckPerms;

        groupPrices = new HashMap<>();
        groupDisplayNames = new HashMap<>();
        luckPermsGroups = new HashMap<>();

        if (plugin.getConfig().getConfigurationSection("home-groups") != null) {
            Objects.requireNonNull(plugin.getConfig().getConfigurationSection("home-groups")).getKeys(false).forEach(key -> {
                double price = plugin.getConfig().getDouble("home-groups." + key + ".price");
                String displayName = plugin.getConfig().getString("home-groups." + key + ".displayName");
                String luckPermsGroup = plugin.getConfig().getString("home-groups." + key + ".luckperms-group");

                groupPrices.put(key, price);
                groupDisplayNames.put(key, displayName);
                luckPermsGroups.put(key, luckPermsGroup);
            });
        }
    }

    public boolean purchaseHomeUpgrade(Player player, String group) {
        Economy econ = TsubusabaUtils.getEconomy();
        if (econ == null) {
            player.sendMessage("§c経済プラグインが見つかりません！");
            return false;
        }

        double price = groupPrices.getOrDefault(group, 0.0);
        String displayName = groupDisplayNames.getOrDefault(group, group);
        String luckPermsGroup = luckPermsGroups.get(group);

        if (price == 0.0) {
            player.sendMessage("§cそのホーム上限は存在しません。");
            return false;
        }

        if (luckPermsGroup == null || luckPermsGroup.isEmpty()) {
            player.sendMessage("§cこのアップグレードに対応するLuckPermsグループが設定されていません。");
            return false;
        }

        net.luckperms.api.model.user.User lpUser = luckPerms.getPlayerAdapter(Player.class).getUser(player);
        if (lpUser.getNodes().stream().anyMatch(node -> node instanceof InheritanceNode && ((InheritanceNode) node).getGroupName().equalsIgnoreCase(luckPermsGroup))) {
            player.sendMessage("§cすでにこのホーム上限は購入済みです！");
            return false;
        }

        if (econ.has(player, price)) {
            econ.withdrawPlayer(player, price);

            // 修正箇所：正しいLuckPermsグループ名を使ってノードを作成
            InheritanceNode node = InheritanceNode.builder(luckPermsGroup).build();
            lpUser.data().add(node);
            luckPerms.getUserManager().saveUser(lpUser);

            player.sendMessage("§aホーム上限 \"§e" + displayName + "§a\" を購入しました！");
            return true;
        } else {
            player.sendMessage("§cお金が足りません！ (必要: §e" + price + "§c)");
            return false;
        }
    }

    public Collection<String> getHomeNames(Player player) {
        User user = essentials.getUser(player);
        if (user == null) return Collections.emptyList();
        List<String> homes = user.getHomes();
        return homes != null ? homes : Collections.emptyList();
    }

    public Location getHome(Player player, String name) {
        User user = essentials.getUser(player);
        if (user == null) return null;
        try {
            return user.getHome(name);
        } catch (Exception e) {
            return null;
        }
    }

    public double getGroupPrice(String key) {
        return groupPrices.getOrDefault(key, 0.0);
    }

    public String getGroupDisplayName(String key) {
        return groupDisplayNames.getOrDefault(key, "Unknown Upgrade");
    }
}