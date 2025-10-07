package net.tsubu.tsubusabautils.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tsubu.tsubusabautils.TsubusabaUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecipeGUIManager implements Listener {

    private final TsubusabaUtils plugin;
    private final Map<Integer, Map<Integer, ItemStack>> pageRecipeItems = new HashMap<>();
    private final Map<Player, Integer> playerCurrentPage = new HashMap<>();
    private static final int CLOSE_SLOT = 53;
    private static final int BACK_SLOT = 45;
    private static final int NEXT_PAGE_SLOT = 50;
    private static final int PREV_PAGE_SLOT = 48;
    private static final int MAX_PAGES = 3;

    public RecipeGUIManager(TsubusabaUtils plugin) {
        this.plugin = plugin;
        initRecipeItems();
    }

    private void initRecipeItems() {
        Map<Integer, ItemStack> page1 = new HashMap<>();
        addRecipeItemToPage(page1, 4, Material.CRAFTING_TABLE, "レシピ - グラップラー", TextColor.color(0xFFD700), null, null, null, null);
        addRecipeItemToPage(page1, 10, Material.ARROW, null, null, null, null, null, null);
        addRecipeItemToPage(page1, 14, Material.ARROW, null, null, null, null, null, null);
        addRecipeItemToPage(page1, 15, Material.DIAMOND, null, null, null, null, null, null);
        addRecipeItemToPage(page1, 20, Material.LEAD, null, null, null, null, null, null);
        addRecipeItemToPage(page1, 21, Material.REDSTONE, null, null, null, null, null, null);
        addRecipeItemToPage(page1, 23, Material.DIAMOND, null, null, null, null, null, null);
        addRecipeItemToPage(page1, 24, Material.ENDER_PEARL, null, null, null, null, null, null);
        addRecipeItemToPage(page1, 25, Material.RABBIT_FOOT, null, null, null, null, null, null);
        addRecipeItemToPage(page1, 29, Material.REDSTONE, null, null, null, null, null, null);
        addRecipeItemToPage(page1, 30, Material.CROSSBOW, null, null, null, null, null, null);
        addRecipeItemToPage(page1, 33, Material.RABBIT_FOOT, null, null, null, null, null, null);
        addRecipeItemToPage(page1, 34, Material.CROSSBOW, null, null, null, null, null, null);

        addRecipeItemToPage(page1, 38, Material.CROSSBOW, "グラップラー", TextColor.color(0xFFD700),
                List.of(Component.text("完成品").decoration(TextDecoration.ITALIC, false),
                        Component.text("40ブロック先まで移動できるぞ!").decoration(TextDecoration.ITALIC, false)),
                Enchantment.PIERCING, 1, null);
        addRecipeItemToPage(page1, 42, Material.CROSSBOW, "レジェンドグラップラー", TextColor.color(0xFF5555),
                List.of(Component.text("完成品").decoration(TextDecoration.ITALIC, false),
                        Component.text("90ブロック先まで移動できるぞ!").decoration(TextDecoration.ITALIC, false)),
                Enchantment.PIERCING, 1, null);

        pageRecipeItems.put(1, page1);

        Map<Integer, ItemStack> page2 = new HashMap<>();
        addRecipeItemToPage(page2, 4, Material.CRAFTING_TABLE, "レシピ - バックパック", TextColor.color(0xFFD700), null, null, null, null);

        addRecipeItemToPage(page2, 10, Material.LEATHER, null, null, null, null, null, null);
        addRecipeItemToPage(page2, 11, Material.IRON_INGOT, null, null, null, null, null, null);
        addRecipeItemToPage(page2, 12, Material.LEATHER, null, null, null, null, null, null);
        addRecipeItemToPage(page2, 19, Material.IRON_INGOT, null, null, null, null, null, null);
        addRecipeItemToPage(page2, 20, Material.CHEST, null, null, null, null, null, null);
        addRecipeItemToPage(page2, 21, Material.IRON_INGOT, null, null, null, null, null, null);
        addRecipeItemToPage(page2, 28, Material.TRIPWIRE_HOOK, null, null, null, null, null, null);
        addRecipeItemToPage(page2, 29, Material.IRON_INGOT, null, null, null, null, null, null);
        addRecipeItemToPage(page2, 30, Material.TRIPWIRE_HOOK, null, null, null, null, null, null);

        addRecipeItemToPage(page2, 38, Material.BROWN_DYE, "冒険者のバックパック", TextColor.color(0xE3680E),
                List.of(Component.text("完成品").decoration(TextDecoration.ITALIC, false),
                        Component.text("9スロットのバックパック").decoration(TextDecoration.ITALIC, false)),
                null, null, 800001);

        addRecipeItemToPage(page2, 14, Material.LEATHER, null, null, null, null, null, null);
        addRecipeItemToPage(page2, 15, Material.DIAMOND, null, null, null, null, null, null);
        addRecipeItemToPage(page2, 16, Material.LEATHER, null, null, null, null, null, null);
        addRecipeItemToPage(page2, 23, Material.DIAMOND, null, null, null, null, null, null);
        addRecipeItemToPage(page2, 24, Material.CHEST, null, null, null, null, null, null);
        addRecipeItemToPage(page2, 25, Material.DIAMOND, null, null, null, null, null, null);
        addRecipeItemToPage(page2, 32, Material.DIAMOND_BLOCK, null, null, null, null, null, null);
        addRecipeItemToPage(page2, 33, Material.DIAMOND, null, null, null, null, null, null);
        addRecipeItemToPage(page2, 34, Material.DIAMOND_BLOCK, null, null, null, null, null, null);

        addRecipeItemToPage(page2, 42, Material.BROWN_DYE, "強化バックパック", TextColor.color(0xE0B715),
                List.of(Component.text("完成品").decoration(TextDecoration.ITALIC, false),
                        Component.text("27スロットのバックパック").decoration(TextDecoration.ITALIC, false)),
                null, null, 800002);

        pageRecipeItems.put(2, page2);

        Map<Integer, ItemStack> page3 = new HashMap<>();
        addRecipeItemToPage(page3, 4, Material.CRAFTING_TABLE, "レシピ - レジェンドバックパック", TextColor.color(0xFFD700), null, null, null, null);

        addRecipeItemToPage(page3, 12, Material.LEATHER, null, null, null, null, null, null);
        addRecipeItemToPage(page3, 13, Material.NETHERITE_INGOT, null, null, null, null, null, null);
        addRecipeItemToPage(page3, 14, Material.LEATHER, null, null, null, null, null, null);
        addRecipeItemToPage(page3, 21, Material.NETHERITE_INGOT, null, null, null, null, null, null);
        addRecipeItemToPage(page3, 22, Material.ENDER_CHEST, null, null, null, null, null, null);
        addRecipeItemToPage(page3, 23, Material.NETHERITE_INGOT, null, null, null, null, null, null);
        addRecipeItemToPage(page3, 30, Material.OBSIDIAN, null, null, null, null, null, null);
        addRecipeItemToPage(page3, 31, Material.NETHER_STAR, null, null, null, null, null, null);
        addRecipeItemToPage(page3, 32, Material.OBSIDIAN, null, null, null, null, null, null);

        addRecipeItemToPage(page3, 40, Material.BROWN_DYE, "レジェンドバックパック", TextColor.color(0xFF5555),
                List.of(Component.text("完成品").decoration(TextDecoration.ITALIC, false),
                        Component.text("54スロットのバックパック").decoration(TextDecoration.ITALIC, false)),
                null, null, 800003);

        pageRecipeItems.put(3, page3);
    }

    private void addRecipeItemToPage(Map<Integer, ItemStack> page, int slot, Material material, String displayName, TextColor color,
                                     List<Component> lore, Enchantment enchant, Integer level, Integer customModelId) {
        if (material == Material.AIR) return;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (displayName != null) {
                Component name = Component.text(displayName);
                if (color != null) name = name.color(color);
                name = name.decoration(TextDecoration.ITALIC, false);
                meta.displayName(name);
            }

            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }

            if (customModelId != null) {
                meta.setCustomModelData(customModelId);
            }

            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            if (enchant != null && level != null) {
                meta.addEnchant(enchant, level, true);
            }

            item.setItemMeta(meta);
        }
        page.put(slot, item);
    }

    public void openRecipePage(Player player, int page) {
        if (page < 1) page = 1;
        if (page > MAX_PAGES) page = MAX_PAGES;

        playerCurrentPage.put(player, page);

        Inventory gui = Bukkit.createInventory(null, 54, Component.text("クラフトレシピ (" + page + "/" + MAX_PAGES + ")"));

        Map<Integer, ItemStack> recipeItems = pageRecipeItems.getOrDefault(page, new HashMap<>());
        for (Map.Entry<Integer, ItemStack> entry : recipeItems.entrySet()) {
            gui.setItem(entry.getKey(), entry.getValue());
        }

        if (page == 1) {
            ItemStack back = new ItemStack(Material.SPECTRAL_ARROW);
            ItemMeta backMeta = back.getItemMeta();
            if (backMeta != null) {
                backMeta.displayName(Component.text("メインメニューに戻る").color(TextColor.color(0xFFD700))
                        .decoration(TextDecoration.ITALIC, false));
                back.setItemMeta(backMeta);
            }
            gui.setItem(BACK_SLOT, back);
        }

        if (page > 1) {
            ItemStack prevPage = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta prevMeta = prevPage.getItemMeta();
            if (prevMeta != null) {
                prevMeta.displayName(Component.text("前のページ").color(TextColor.color(0x55FF55))
                        .decoration(TextDecoration.ITALIC, false));
                prevPage.setItemMeta(prevMeta);
            }
            gui.setItem(PREV_PAGE_SLOT, prevPage);
        }

        if (page < MAX_PAGES) {
            ItemStack nextPage = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            ItemMeta nextMeta = nextPage.getItemMeta();
            if (nextMeta != null) {
                nextMeta.displayName(Component.text("次のページ").color(TextColor.color(0x55FF55))
                        .decoration(TextDecoration.ITALIC, false));
                nextPage.setItemMeta(nextMeta);
            }
            gui.setItem(NEXT_PAGE_SLOT, nextPage);
        }

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.displayName(Component.text("閉じる").color(TextColor.color(0xFF5555))
                    .decoration(TextDecoration.ITALIC, false));
            close.setItemMeta(closeMeta);
        }
        gui.setItem(CLOSE_SLOT, close);

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);

        Set<Integer> blankSlots;
        if (page == 1) {
            blankSlots = Set.of(11, 12, 16, 19, 28, 32);
        } else {
            blankSlots = Set.of();
        }

        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null && !blankSlots.contains(i)) {
                gui.setItem(i, filler);
            }
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().title().toString();
        if (!title.contains("クラフトレシピ")) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        int currentPage = playerCurrentPage.getOrDefault(player, 1);

        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            playerCurrentPage.remove(player);
        } else if (slot == BACK_SLOT && currentPage == 1) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            player.performCommand("menu");
            playerCurrentPage.remove(player);
        } else if (slot == NEXT_PAGE_SLOT && currentPage < MAX_PAGES) {
            openRecipePage(player, currentPage + 1);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        } else if (slot == PREV_PAGE_SLOT && currentPage > 1) {
            openRecipePage(player, currentPage - 1);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().title().toString();
        if (title.contains("クラフトレシピ")) {
            event.setCancelled(true);
        }
    }
}