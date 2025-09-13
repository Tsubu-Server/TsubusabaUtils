package net.tsubu.tsubusabautils.manager;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public class HomeGUIManager implements Listener {

    private final HomeManager homeManager;
    private final JavaPlugin plugin;

    public HomeGUIManager(JavaPlugin plugin, HomeManager homeManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openHomeGUI(Player player) {
        Collection<String> homes = homeManager.getHomeNames(player);
        int size = Math.max(9, ((homes.size() + 1 + 8) / 9) * 9);
        Inventory inv = Bukkit.createInventory(null, size, LegacyComponentSerializer.legacyAmpersand().deserialize("§bホーム一覧"));

        for (String homeName : homes) {
            Location homeLoc = homeManager.getHome(player, homeName);
            if (homeLoc != null) {
                String worldName = homeLoc.getWorld().getName();
                String coords = String.format("§7座標: X:%.1f, Y:%.1f, Z:%.1f", homeLoc.getX(), homeLoc.getY(), homeLoc.getZ());
                String world = "§7ワールド: " + worldName;

                ItemStack homeItem = createGuiItem(Material.BOOK, "§e" + homeName, "§7クリックでテレポート", world, coords);
                inv.addItem(homeItem);
            }
        }

        ItemStack purchaseItem = createGuiItem(Material.EMERALD, "§aホーム上限購入", "§7クリックでホーム上限を購入");
        inv.setItem(size - 1, purchaseItem);

        player.openInventory(inv);
    }

    public void openPurchaseGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, LegacyComponentSerializer.legacyAmpersand().deserialize("§bホーム上限購入"));

        String displayName1 = homeManager.getGroupDisplayName("home-upgrade-1");
        double price1 = homeManager.getGroupPrice("home-upgrade-1");
        ItemStack upgrade1 = createGuiItem(Material.DIAMOND_HOE, "§e" + displayName1, "§7価格: §6" + price1);
        inv.setItem(11, upgrade1);

        String displayName2 = homeManager.getGroupDisplayName("home-upgrade-2");
        double price2 = homeManager.getGroupPrice("home-upgrade-2");
        ItemStack upgrade2 = createGuiItem(Material.NETHERITE_HOE, "§e" + displayName2, "§7価格: §6" + price2);
        inv.setItem(15, upgrade2);

        ItemStack backButton = createGuiItem(Material.BARRIER, "§c戻る", "§7ホーム一覧に戻ります");
        inv.setItem(22, backButton);

        player.openInventory(inv);
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(name));
        meta.lore(Arrays.stream(lore).map(line -> LegacyComponentSerializer.legacyAmpersand().deserialize(line)).collect(Collectors.toList()));
        item.setItemMeta(meta);
        return item;
    }

    public boolean teleportToHome(Player player, String homeName) {
        Location homeLoc = homeManager.getHome(player, homeName);
        if (homeLoc != null) {
            player.teleport(homeLoc);
            player.sendMessage("§aホームに移動しました: " + homeName);
            return true;
        }
        return false;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;

        String inventoryTitle = LegacyComponentSerializer.legacyAmpersand().serialize(event.getView().title()).trim();

        if (inventoryTitle.equals("§bホーム一覧") || inventoryTitle.equals("§bホーム上限購入")) {
            event.setCancelled(true);
        } else {
            return;
        }

        if (inventoryTitle.equals("§bホーム一覧")) {
            if (event.getCurrentItem().getType() == Material.BOOK) {
                String homeName = LegacyComponentSerializer.legacyAmpersand().serialize(event.getCurrentItem().getItemMeta().displayName()).trim().replace("§e", "");
                teleportToHome(player, homeName);
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            } else if (event.getCurrentItem().getType() == Material.EMERALD) {
                openPurchaseGUI(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
        } else if (inventoryTitle.equals("§bホーム上限購入")) {
            if (event.getCurrentItem().getType() == Material.DIAMOND_HOE) {
                boolean success = homeManager.purchaseHomeUpgrade(player, "home-upgrade-1");
                if (success) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
                }
                player.closeInventory();
            } else if (event.getCurrentItem().getType() == Material.NETHERITE_HOE) {
                boolean success = homeManager.purchaseHomeUpgrade(player, "home-upgrade-2");
                if (success) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
                }
                player.closeInventory();
            } else if (event.getCurrentItem().getType() == Material.BARRIER) {
                openHomeGUI(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
        }
    }
}