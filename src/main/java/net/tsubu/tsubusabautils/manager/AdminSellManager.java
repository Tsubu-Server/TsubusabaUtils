package net.tsubu.tsubusabautils.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.tsubu.tsubusabautils.TsubusabaUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

public class AdminSellManager implements Listener {

    private final TsubusabaUtils plugin;
    private final Economy economy;
    private final Map<Material, Double> buybackItems = new HashMap<>();
    private final Map<Material, Integer> buybackSlots = new HashMap<>();
    private final Map<Material, String> buybackNames = new HashMap<>();
    private String nextUpdateDate = "未設定";
    private final DecimalFormat df = new DecimalFormat("#,##0.##");
    private File configFile;
    private FileConfiguration config;
    private final Set<UUID> skipReturnOnClose = Collections.synchronizedSet(new HashSet<>());

    public AdminSellManager(TsubusabaUtils plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        setupConfig();
        loadConfig();
    }

    private void setupConfig() {
        configFile = new File(plugin.getDataFolder(), "adminsell.yml");
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
                config = YamlConfiguration.loadConfiguration(configFile);
                config.set("update", "2025-09-21");

                List<Map<String, Object>> items = new ArrayList<>();
                Map<String, Object> stone = new HashMap<>();
                stone.put("material", "STONE");
                stone.put("price", 2);
                stone.put("slot", 1);
                stone.put("display_name", "石");
                items.add(stone);

                Map<String, Object> oakLog = new HashMap<>();
                oakLog.put("material", "OAK_LOG");
                oakLog.put("price", 5);
                oakLog.put("slot", 3);
                oakLog.put("display_name", "オークの原木");
                items.add(oakLog);

                Map<String, Object> wheat = new HashMap<>();
                wheat.put("material", "WHEAT");
                wheat.put("price", 3);
                wheat.put("slot", 5);
                wheat.put("display_name", "小麦");
                items.add(wheat);

                config.set("items", items);
                config.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create adminsell.yml file: " + e.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void loadConfig() {
        buybackItems.clear();
        buybackSlots.clear();
        buybackNames.clear();
        nextUpdateDate = config.getString("update", "未設定");

        List<?> list = config.getList("items");
        if (list != null) {
            for (Object o : list) {
                if (!(o instanceof Map)) continue;
                Map<?, ?> m = (Map<?, ?>) o;
                try {
                    String materialName = String.valueOf(m.get("material"));
                    Object priceObj = m.get("price");
                    Object slotObj = m.get("slot");
                    String displayName = m.get("display_name") != null ? String.valueOf(m.get("display_name")) : materialName;

                    Material material = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
                    double price;
                    int slot = 1;

                    if (priceObj instanceof Number) price = ((Number) priceObj).doubleValue();
                    else price = Double.parseDouble(String.valueOf(priceObj));

                    if (slotObj instanceof Number) slot = ((Number) slotObj).intValue();
                    else if (slotObj != null) slot = Integer.parseInt(String.valueOf(slotObj));

                    if (slot < 0) slot = 0;
                    if (slot > 18) slot = 18;
                    int guiSlot = (slot >= 1 && slot <= 18) ? (slot - 1) : slot;

                    buybackItems.put(material, price);
                    buybackSlots.put(material, guiSlot);
                    buybackNames.put(material, displayName);

                } catch (Exception ex) {
                    plugin.getLogger().warning("Invalid buyback item in adminsell.yml: " + m + " — " + ex.getMessage());
                }
            }
        }
        plugin.getLogger().info("Loaded admin sell items: " + buybackItems.size() + " items (next update: " + nextUpdateDate + ")");
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        loadConfig();
    }

    public void openBuybackGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54,
                Component.text("今週の買取 (次の更新: " + nextUpdateDate + ")")
                        .color(NamedTextColor.DARK_GREEN)
                        .decoration(TextDecoration.BOLD,true)
                        .decoration(TextDecoration.ITALIC, false)
        );

        for (int i = 0; i <= 17; i++) {
            ItemStack glass = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
            glass.editMeta(meta -> meta.displayName(Component.text(" ")));
            gui.setItem(i, glass);
        }
        int[] buttonGlassSlots = {46, 47, 48, 50, 51, 52};
        for (int slot : buttonGlassSlots) {
            ItemStack glass = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
            glass.editMeta(meta -> meta.displayName(Component.text(" ")));
            gui.setItem(slot, glass);
        }

        for (Map.Entry<Material, Double> entry : buybackItems.entrySet()) {
            Material material = entry.getKey();
            double price = entry.getValue();
            int slot = buybackSlots.getOrDefault(material, 0);
            String displayName = buybackNames.getOrDefault(material, material.name());

            ItemStack item = new ItemStack(material);
            item.editMeta(meta -> {
                meta.displayName(Component.text(displayName)
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("買取価格: " + df.format(price) + "D / 個")
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text(""));
                lore.add(Component.text("下のスロットに売却したいアイテムを置いてください")
                        .color(NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
            });
            gui.setItem(slot, item);
        }

        ItemStack back = new ItemStack(Material.ARROW);
        back.editMeta(meta -> {
            meta.displayName(Component.text("戻る").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("運営ショップメニューに戻る").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
        });
        gui.setItem(45, back);

        ItemStack sell = new ItemStack(Material.EMERALD_BLOCK);
        sell.editMeta(meta -> {
            meta.displayName(Component.text("売却").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("クリック/タップで売却").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
            ));
        });
        gui.setItem(49, sell);

        ItemStack close = new ItemStack(Material.BARRIER);
        close.editMeta(meta -> {
            meta.displayName(Component.text("閉じる").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("メニューを閉じます").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        });
        gui.setItem(53, close);

        player.openInventory(gui);
        updateSellButton(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Component title = player.getOpenInventory().title();
        if (title == null) return;
        String titleText = PlainTextComponentSerializer.plainText().serialize(title);
        if (!titleText.contains("今週の買取")) return;

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;
        if (clickedInv.equals(event.getView().getTopInventory())) {
            int slot = event.getSlot();
            if ((slot >= 0 && slot <= 17) || (slot >= 45 && slot <= 53)) {
                event.setCancelled(true);

                if (slot == 45 || slot == 49 || slot == 53) {
                    ItemStack clicked = event.getCurrentItem();
                    if (clicked == null || !clicked.hasItemMeta() || clicked.getItemMeta().displayName() == null) return;
                    String name = PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().displayName());
                    switch (name) {
                        case "戻る" -> { player.closeInventory(); player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK,1f,1f); }
                        case "閉じる" -> { player.closeInventory(); player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK,1f,1f); }
                        case "売却" -> processSell(player);
                    }
                }
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> updateSellButton(event.getView().getTopInventory()));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String titleText = PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title());
        if (!titleText.contains("今週の買取")) return;

        Bukkit.getScheduler().runTask(plugin, () -> updateSellButton(event.getView().getTopInventory()));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getView().title() == null) return;
        String titleText = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!titleText.contains("今週の買取")) return;

        UUID uuid = player.getUniqueId();
        if (skipReturnOnClose.contains(uuid)) { skipReturnOnClose.remove(uuid); return; }

        Inventory inv = event.getView().getTopInventory();
        for (int i = 18; i <= 44; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                player.getInventory().addItem(item).values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
                inv.setItem(i,null);
            }
        }
    }

    private void updateSellButton(Inventory inv) {
        double total = 0.0;
        boolean hadInvalidItems = false;

        for (int i = 18; i <= 44; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            if (item.getItemMeta() instanceof BlockStateMeta bsm && item.getType().name().endsWith("SHULKER_BOX")) {
                ShulkerBox box = (ShulkerBox) bsm.getBlockState();
                for (ItemStack inside : box.getInventory().getContents()) {
                    if (inside == null) continue;
                    if (buybackItems.containsKey(inside.getType())) {
                        total += buybackItems.get(inside.getType()) * inside.getAmount();
                    } else {
                        hadInvalidItems = true;
                    }
                }
            } else {
                if (buybackItems.containsKey(item.getType())) {
                    total += buybackItems.get(item.getType()) * item.getAmount();
                } else {
                    hadInvalidItems = true;
                }
            }
        }

        ItemStack confirm = inv.getItem(49);
        if (confirm != null && confirm.getType() != Material.AIR) {
            ItemMeta meta = confirm.getItemMeta();
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("クリック/タップで売却")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            if (total <= 0) {
                lore.add(Component.text("売却できるアイテムがありません")
                        .color(NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false)
                );
            } else if (hadInvalidItems) {
                lore.add(Component.text("売却不可アイテムは返品されます")
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
                );
            }
            lore.add(Component.text("合計: " + df.format(total) + "D")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            confirm.setItemMeta(meta);
        }
    }


    private void processSell(Player player) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        if (inv == null) return;

        double total = 0.0;
        boolean hadInvalidItems = false;

        for (int i = 18; i <= 44; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            double itemTotal = 0.0;

            if (item.getItemMeta() instanceof BlockStateMeta bsm && item.getType().name().endsWith("SHULKER_BOX")) {
                ShulkerBox box = (ShulkerBox) bsm.getBlockState();
                Inventory boxInv = box.getInventory();
                List<ItemStack> toRemove = new ArrayList<>();

                for (ItemStack inside : boxInv.getContents()) {
                    if (inside == null) continue;
                    if (buybackItems.containsKey(inside.getType())) {
                        itemTotal += buybackItems.get(inside.getType()) * inside.getAmount();
                        toRemove.add(inside);
                    } else {
                        hadInvalidItems = true;
                    }
                }

                for (ItemStack removeItem : toRemove) {
                    boxInv.remove(removeItem);
                }

                bsm.setBlockState(box);
                item.setItemMeta(bsm);
                inv.setItem(i, item);

            } else {
                if (!buybackItems.containsKey(item.getType())) {
                    hadInvalidItems = true;
                    continue;
                }
                itemTotal += buybackItems.get(item.getType()) * item.getAmount();
                inv.setItem(i, null);
            }

            total += itemTotal;
        }

        if (total <= 0) {
//            player.sendMessage(Component.text("売却できるアイテムがありません").color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        EconomyResponse resp = economy.depositPlayer(player, total);
        if (resp.transactionSuccess()) {
            player.sendMessage(Component.text("合計" + df.format(total) + "D を受け取りました").color(NamedTextColor.GREEN));
            if (hadInvalidItems) {
                player.sendMessage(Component.text("売却不可アイテムは返品しました").color(NamedTextColor.YELLOW));
            }
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
            player.closeInventory();
        } else {
            player.sendMessage(Component.text("支払い処理でエラーが発生しました").color(NamedTextColor.RED));
        }
    }
}
