package net.tsubu.tsubusabautils.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.tsubu.tsubusabautils.TsubusabaUtils;
import org.bukkit.*;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

public class AdminSellCategory implements Listener {

    private final TsubusabaUtils plugin;
    private final Economy economy;
    private final String name;

    private final DecimalFormat df = new DecimalFormat("#,##0.##");

    private final Map<Material, Double> buybackItems = new HashMap<>();
    private final Map<Material, Integer> buybackSlots = new HashMap<>();
    private final Map<Material, String> buybackNames = new HashMap<>();

    private String nextUpdateDate = "未設定";
    private File configFile;
    private FileConfiguration config;

    public AdminSellCategory(TsubusabaUtils plugin, Economy economy, String name) {
        this.plugin = plugin;
        this.economy = economy;
        this.name = name;
        setupConfig();
        loadConfig();

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void setupConfig() {
        String fileName = name.equalsIgnoreCase("default") ? "adminsell.yml" : "adminsell_" + name + ".yml";
        configFile = new File(plugin.getDataFolder(), fileName);
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
                config = YamlConfiguration.loadConfiguration(configFile);
                config.set("update", "2025-10-31");

                List<Map<String, Object>> items = new ArrayList<>();
                Map<String, Object> sample = new HashMap<>();
                sample.put("material", "STONE");
                sample.put("price", 2);
                sample.put("slot", 1);
                sample.put("display_name", "石");
                items.add(sample);
                config.set("items", items);
                config.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create " + fileName + ": " + e.getMessage());
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
                if (!(o instanceof Map<?, ?> map)) continue;
                try {
                    String materialName = String.valueOf(map.get("material"));
                    Material material = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
                    double price = Double.parseDouble(String.valueOf(map.get("price")));
                    int slot = Integer.parseInt(String.valueOf(map.get("slot")));

                    Object displayObj = map.get("display_name");
                    String display = (displayObj != null) ? String.valueOf(displayObj) : materialName;

                    buybackItems.put(material, price);
                    buybackSlots.put(material, slot);
                    buybackNames.put(material, display);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Invalid item in " + name + ": " + ex.getMessage());
                }
            }
        }
        plugin.getLogger().info("Loaded AdminSell category '" + name + "' (" + buybackItems.size() + " items)");
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);
        loadConfig();
    }

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54,
                Component.text("今週の買取 [" + name + "] (次更新: " + nextUpdateDate + ")")
                        .color(NamedTextColor.DARK_GREEN)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false)
        );

        for (int i = 0; i < 18; i++) {
            ItemStack glass = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
            glass.editMeta(m -> m.displayName(Component.text(" ")));
            gui.setItem(i, glass);
        }

        for (Map.Entry<Material, Double> entry : buybackItems.entrySet()) {
            Material mat = entry.getKey();
            double price = entry.getValue();
            int slot = Math.max(0, Math.min(17, buybackSlots.getOrDefault(mat, 0)));
            String display = buybackNames.getOrDefault(mat, mat.name());

            ItemStack item = new ItemStack(mat);
            item.editMeta(meta -> {
                meta.displayName(Component.text(display).color(NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true));
                meta.lore(List.of(
                        Component.text("買取価格: " + df.format(price) + "D/個").color(NamedTextColor.GREEN),
                        Component.text("下に売りたいアイテムを置いてください").color(NamedTextColor.AQUA)
                ));
            });
            gui.setItem(slot, item);
        }

        ItemStack sell = new ItemStack(Material.EMERALD_BLOCK);
        sell.editMeta(meta -> meta.displayName(Component.text("売却").color(NamedTextColor.GREEN)));
        gui.setItem(49, sell);

        player.openInventory(gui);
    }

    public void processSell(Player player, Inventory inv) {
        double total = 0.0;
        boolean invalid = false;

        for (int i = 18; i <= 44; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            if (item.getItemMeta() instanceof BlockStateMeta bsm &&
                    item.getType().name().endsWith("SHULKER_BOX")) {
                ShulkerBox box = (ShulkerBox) bsm.getBlockState();
                for (ItemStack inside : box.getInventory().getContents()) {
                    if (inside == null) continue;
                    if (buybackItems.containsKey(inside.getType())) {
                        total += buybackItems.get(inside.getType()) * inside.getAmount();
                    } else invalid = true;
                }
                inv.setItem(i, item);
            } else {
                if (buybackItems.containsKey(item.getType())) {
                    total += buybackItems.get(item.getType()) * item.getAmount();
                    inv.setItem(i, null);
                } else invalid = true;
            }
        }

        if (total <= 0) {
            player.sendMessage(Component.text("売却できるアイテムがありません。").color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        EconomyResponse res = economy.depositPlayer(player, total);
        if (res.transactionSuccess()) {
            player.sendMessage(Component.text("合計 " + df.format(total) + "D を受け取りました。").color(NamedTextColor.GREEN));
            if (invalid)
                player.sendMessage(Component.text("売却不可アイテムは返品されました。").color(NamedTextColor.YELLOW));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
            player.closeInventory();
        } else {
            player.sendMessage(Component.text("支払い処理中にエラーが発生しました。").color(NamedTextColor.RED));
        }
    }
}
