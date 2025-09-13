package net.tsubu.tsubusabautils.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.tsubu.tsubusabautils.TsubusabaUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class AmountGUIManager implements Listener {

    private final TsubusabaUtils plugin;
    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    private static final long COOLDOWN_MS = 100;

    public AmountGUIManager(TsubusabaUtils plugin) {
        this.plugin = plugin;
    }

    public void open(Player sender, Player target, double amount) {
        Inventory gui = Bukkit.createInventory(null, 36,
                Component.text("送金先： " + target.getName())
                        .color(NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD));

        gui.setItem(25, createDisplayItem(sender, target, amount));

        // 金額増減ボタン (+)
        gui.setItem(10, createButton(Material.GREEN_STAINED_GLASS_PANE, "+1"));
        gui.setItem(11, createButton(Material.GREEN_STAINED_GLASS_PANE, "+10"));
        gui.setItem(12, createButton(Material.GREEN_STAINED_GLASS_PANE, "+100"));
        gui.setItem(13, createButton(Material.GREEN_STAINED_GLASS_PANE, "+1000"));
        gui.setItem(14, createButton(Material.GREEN_STAINED_GLASS_PANE, "+10000"));

        // 金額増減ボタン (-)
        gui.setItem(19, createButton(Material.RED_STAINED_GLASS_PANE, "-1"));
        gui.setItem(20, createButton(Material.RED_STAINED_GLASS_PANE, "-10"));
        gui.setItem(21, createButton(Material.RED_STAINED_GLASS_PANE, "-100"));
        gui.setItem(22, createButton(Material.RED_STAINED_GLASS_PANE, "-1000"));
        gui.setItem(23, createButton(Material.RED_STAINED_GLASS_PANE, "-10000"));

        gui.setItem(27, createButton(Material.ARROW, "戻る"));
        gui.setItem(16, createButton(Material.EMERALD_BLOCK, "送金する"));
        gui.setItem(35, createButton(Material.BARRIER, "閉じる"));

        sender.openInventory(gui);
    }

    private ItemStack createDisplayItem(Player sender, Player target, double amount) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> {
            meta.displayName(Component.text("金額: " + amount).color(NamedTextColor.YELLOW));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("送金先: " + target.getName()).color(NamedTextColor.AQUA));
            lore.add(Component.text("送金する金額: " + amount).color(NamedTextColor.GOLD));

            Economy eco = TsubusabaUtils.getEconomy();
            if (eco != null) {
                lore.add(Component.text("あなたの残高: " + eco.format(eco.getBalance(sender)))
                        .color(NamedTextColor.GREEN));
            }
            meta.lore(lore);
        });
        return item;
    }

    private ItemStack createButton(Material material, String name) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(name)
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
        });
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) return;

        Component titleComponent = player.getOpenInventory().title();
        if (titleComponent == null) return;

        String titleText = PlainTextComponentSerializer.plainText().serialize(titleComponent);
        if (!titleText.startsWith("送金先：")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta() || clicked.getItemMeta().displayName() == null) return;

        long currentTime = System.currentTimeMillis();
        if (lastClickTime.containsKey(player.getUniqueId()) &&
                currentTime - lastClickTime.get(player.getUniqueId()) < COOLDOWN_MS) {
            return;
        }
        lastClickTime.put(player.getUniqueId(), currentTime);

        String name = PlainTextComponentSerializer.plainText()
                .serialize(clicked.getItemMeta().displayName());

        Inventory gui = event.getInventory();

        ItemStack display = gui.getItem(25);
        double amount = 0.0;
        Player target;
        if (display != null && display.hasItemMeta() && display.getItemMeta().displayName() != null) {
            String displayName = PlainTextComponentSerializer.plainText()
                    .serialize(display.getItemMeta().displayName());
            try {
                amount = Double.parseDouble(displayName.replace("金額: ", "").trim());
            } catch (NumberFormatException e) {
                amount = 0.0;
            }
        }

        String targetName = titleText.replace("送金先：", "").trim();
        target = Bukkit.getPlayer(targetName);

        switch (name) {
            case "+1", "+10", "+100", "+1000", "+10000",
                 "-1", "-10", "-100", "-1000", "-10000" -> {
                if (name.startsWith("+")) {
                    amount += Integer.parseInt(name.substring(1));
                } else {
                    amount = Math.max(0, amount - Integer.parseInt(name.substring(1)));
                }

                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

                ItemMeta meta = clicked.getItemMeta();
                meta.addEnchant(Enchantment.INFINITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                clicked.setItemMeta(meta);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    ItemMeta newMeta = clicked.getItemMeta();
                    newMeta.removeEnchant(Enchantment.INFINITY);
                    clicked.setItemMeta(newMeta);
                }, 1L);
            }
            case "戻る" -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                plugin.getGuiManager().openGUI(player, 0);
                return;
            }
            case "送金する" -> {
                if (target != null) {
                    plugin.getChatManager().processPayment(player, target, amount);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                }
                player.closeInventory();
                return;
            }
            case "閉じる" -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.closeInventory();
                return;
            }
        }

        gui.setItem(25, createDisplayItem(player, target, amount));
    }
}