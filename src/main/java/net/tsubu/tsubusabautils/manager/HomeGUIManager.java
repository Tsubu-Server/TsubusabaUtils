package net.tsubu.tsubusabautils.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("ホーム一覧")
                .color(NamedTextColor.BLUE)
                .decoration(TextDecoration.ITALIC, false));

        int slot = 0;
        for (String homeName : homes) {
            if (slot >= 18) break; // 最大18個のホーム（戻る・閉じるボタン用にスペースを確保）

            Location homeLoc = homeManager.getHome(player, homeName);
            if (homeLoc != null) {
                String worldName = homeLoc.getWorld().getName();

                ItemStack homeItem = new ItemStack(Material.BOOK);
                homeItem.editMeta(meta -> {
                    meta.displayName(Component.text(homeName)
                            .color(NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false));

                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.text("クリック/タップでテレポート")
                            .color(NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("ワールド: " + worldName)
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("座標: X:" + (int)homeLoc.getX() + ", Y:" + (int)homeLoc.getY() + ", Z:" + (int)homeLoc.getZ())
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));

                    meta.lore(lore);
                });

                inv.setItem(slot, homeItem);
                slot++;
            }
        }

        // ホーム上限購入ボタン
        ItemStack purchaseItem = new ItemStack(Material.EMERALD);
        purchaseItem.editMeta(meta -> {
            meta.displayName(Component.text("ホーム上限購入")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("クリック/タップでホーム上限を購入")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        inv.setItem(18, purchaseItem);

        // 閉じるボタン
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        closeItem.editMeta(meta -> {
            meta.displayName(Component.text("閉じる")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("メニューを閉じます")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        inv.setItem(26, closeItem);

        player.openInventory(inv);
    }

    public void openPurchaseGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("ホーム上限購入")
                .color(NamedTextColor.BLUE)
                .decoration(TextDecoration.ITALIC, false));

        String displayName1 = homeManager.getGroupDisplayName("home-upgrade-1");
        double price1 = homeManager.getGroupPrice("home-upgrade-1");
        ItemStack upgrade1 = new ItemStack(Material.DIAMOND_HOE);
        upgrade1.editMeta(meta -> {
            meta.displayName(Component.text(displayName1)
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("価格: " + price1 + "$")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("クリック/タップで購入")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        inv.setItem(11, upgrade1);

        String displayName2 = homeManager.getGroupDisplayName("home-upgrade-2");
        double price2 = homeManager.getGroupPrice("home-upgrade-2");
        ItemStack upgrade2 = new ItemStack(Material.NETHERITE_HOE);
        upgrade2.editMeta(meta -> {
            meta.displayName(Component.text(displayName2)
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("価格: " + price2 + "$")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("クリック/タップで購入")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        inv.setItem(15, upgrade2);

        // 戻るボタン
        ItemStack backButton = new ItemStack(Material.ARROW);
        backButton.editMeta(meta -> {
            meta.displayName(Component.text("戻る")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("ホーム一覧に戻ります")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        inv.setItem(18, backButton);

        // 閉じるボタン
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        closeButton.editMeta(meta -> {
            meta.displayName(Component.text("閉じる")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("メニューを閉じます")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        inv.setItem(26, closeButton);

        player.openInventory(inv);
    }

    public boolean teleportToHome(Player player, String homeName) {
        Location homeLoc = homeManager.getHome(player, homeName);
        if (homeLoc != null) {
            player.teleport(homeLoc);
            player.sendMessage(Component.text("ホームに移動しました: " + homeName)
                    .color(NamedTextColor.GREEN));
            return true;
        }
        return false;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;

        Component titleComponent = player.getOpenInventory().title();
        if (titleComponent == null) return;

        String titleText = PlainTextComponentSerializer.plainText().serialize(titleComponent);

        if (titleText.equals("ホーム一覧") || titleText.equals("ホーム上限購入")) {
            event.setCancelled(true);
        } else {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        String itemName = PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().displayName());

        if (titleText.equals("ホーム一覧")) {
            if (clicked.getType() == Material.BOOK) {
                teleportToHome(player, itemName);
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            } else if (clicked.getType() == Material.EMERALD) {
                openPurchaseGUI(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            } else if (clicked.getType() == Material.BARRIER && itemName.equals("閉じる")) {
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
        } else if (titleText.equals("ホーム上限購入")) {
            if (clicked.getType() == Material.DIAMOND_HOE) {
                boolean success = homeManager.purchaseHomeUpgrade(player, "home-upgrade-1");
                if (success) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                player.closeInventory();
            } else if (clicked.getType() == Material.NETHERITE_HOE) {
                boolean success = homeManager.purchaseHomeUpgrade(player, "home-upgrade-2");
                if (success) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                player.closeInventory();
            } else if (clicked.getType() == Material.ARROW && itemName.equals("戻る")) {
                openHomeGUI(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            } else if (clicked.getType() == Material.BARRIER && itemName.equals("閉じる")) {
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
        }
    }
}