package net.tsubu.tsubusabautils.manager;

import net.tsubu.tsubusabautils.TsubusabaUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;

import java.text.DecimalFormat;
import java.util.*;

public class GUIManager implements Listener {

    private final TsubusabaUtils plugin;
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private static final int ITEMS_PER_PAGE = 45;
    private static final DecimalFormat df = new DecimalFormat("#,##0.#");

    public GUIManager(TsubusabaUtils plugin) {
        this.plugin = plugin;
    }

    public void openGUI(Player player, int page) {
        List<Player> onlinePlayers = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(player)) {
                onlinePlayers.add(p);
            }
        }

        int totalPages = (int) Math.ceil((double) onlinePlayers.size() / ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;

        page = Math.max(0, Math.min(page, totalPages - 1));
        playerPages.put(player.getUniqueId(), page);

        Inventory gui = Bukkit.createInventory(null, 54,
                Component.text("送金 - ページ " + (page + 1) + "/" + totalPages)
                        .color(NamedTextColor.DARK_GREEN)
                        .decorate(TextDecoration.BOLD));

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, onlinePlayers.size());

        for (int i = startIndex; i < endIndex; i++) {
            Player target = onlinePlayers.get(i);
            ItemStack head = createPlayerHead(target);
            gui.setItem(i - startIndex, head);
        }

        if (page > 0) {
            gui.setItem(48, createNavigationItem(Material.ORANGE_DYE, "◀ 前のページ", NamedTextColor.YELLOW));
        }
        if (page < totalPages - 1) {
            gui.setItem(50, createNavigationItem(Material.LIME_DYE, "次のページ ▶", NamedTextColor.YELLOW));
        }
        gui.setItem(53, createNavigationItem(Material.BARRIER, "閉じる", NamedTextColor.RED));
        gui.setItem(45, createNavigationItem(Material.SPECTRAL_ARROW, "メインメニューに戻る", NamedTextColor.GOLD));

        ItemStack playerSelfHead = createPlayerSelfHead(player);
        gui.setItem(49, playerSelfHead);

        player.openInventory(gui);
    }

    private ItemStack createPlayerHead(Player target) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        meta.setOwningPlayer(target);
        meta.displayName(Component.text(target.getName())
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = Arrays.asList(
                Component.text("クリック/タップで送金")
                        .color(NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("オンライン")
                        .color(NamedTextColor.GREEN)
                        .decorate(TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false)
        );
        meta.lore(lore);

        head.setItemMeta(meta);
        return head;
    }

    private ItemStack createPlayerSelfHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        meta.setOwningPlayer(player);
        meta.displayName(Component.text(player.getName())
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

        Economy economy = TsubusabaUtils.getEconomy();
        double balance = 0.0;
        if (economy != null) {
            balance = economy.getBalance(player);
        }

        List<Component> lore = Arrays.asList(
                Component.text("所持金: " + df.format(balance) + "D")
                        .color(NamedTextColor.YELLOW)
                        .decorate(TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false)
        );
        meta.lore(lore);

        head.setItemMeta(meta);
        return head;
    }

    private ItemStack createNavigationItem(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(Component.text(name)
                .color(color)
                .decoration(TextDecoration.ITALIC, false)));
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
        if (!titleText.contains("送金")) return;

        if (clickedInventory.equals(player.getInventory()) &&
                (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT)) {
            event.setCancelled(true);
            return;
        }

        Inventory topInventory = player.getOpenInventory().getTopInventory();
        if (clickedInventory.equals(topInventory)) {
            event.setCancelled(true);

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            String itemName = "";
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta != null && meta.displayName() != null) {
                itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            } else {
                itemName = clickedItem.getType().toString();
            }

            int slot = event.getSlot();

            if (slot == 48 && itemName.contains("前のページ")) {
                int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);
                if (currentPage > 0) openGUI(player, currentPage - 1);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            } else if (slot == 50 && itemName.contains("次のページ")) {
                int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);
                openGUI(player, currentPage + 1);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            } else if (slot == 53 && itemName.contains("閉じる")) {
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            } else if (slot == 45 && itemName.contains("メインメニューに戻る")) {
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.performCommand("menu");
            } else if (clickedItem.getType() == Material.PLAYER_HEAD) {
                if (slot < 45) {
                    if (meta instanceof SkullMeta skullMeta && skullMeta.getOwningPlayer() != null) {
                        String targetName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
                        Player target = Bukkit.getPlayer(targetName);

                        if (target != null && target.isOnline()) {
                            player.closeInventory();
                            plugin.getAmountGUIManager().open(player, target, 0);
                        } else {
                            player.sendMessage(Component.text(targetName + " はオフラインです！")
                                    .color(NamedTextColor.RED));
                        }
                    }
                }
            }
        }
    }
}