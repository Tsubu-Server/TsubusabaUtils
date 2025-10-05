package net.tsubu.tsubusabautils.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tsubu.tsubusabautils.TsubusabaUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
    private final Map<Integer, ItemStack> recipeItems = new HashMap<>();
    private static final int CLOSE_SLOT = 49;
    private static final int BACK_SLOT = 45;

    public RecipeGUIManager(TsubusabaUtils plugin) {
        this.plugin = plugin;
        initRecipeItems();
    }

    private void initRecipeItems() {
        addRecipeItem(4, Material.CRAFTING_TABLE, "レシピ", TextColor.color(0xFFD700), null, null, null);
        addRecipeItem(10, Material.ARROW, null, null, null, null, null);
        addRecipeItem(11, Material.AIR, null, null, null, null, null);
        addRecipeItem(12, Material.AIR, null, null, null, null, null);
        addRecipeItem(14, Material.ARROW, null, null, null, null, null);
        addRecipeItem(15, Material.DIAMOND, null, null, null, null, null);
        addRecipeItem(16, Material.AIR, null, null, null, null, null);
        addRecipeItem(19, Material.AIR, null, null, null, null, null);
        addRecipeItem(20, Material.LEAD, null, null, null, null, null);
        addRecipeItem(21, Material.REDSTONE, null, null, null, null, null);
        addRecipeItem(23, Material.DIAMOND, null, null, null, null, null);
        addRecipeItem(24, Material.ENDER_PEARL, null, null, null, null, null);
        addRecipeItem(25, Material.RABBIT_FOOT, null, null, null, null, null);
        addRecipeItem(28, Material.AIR, null, null, null, null, null);
        addRecipeItem(29, Material.REDSTONE, null, null, null, null, null);
        addRecipeItem(30, Material.CROSSBOW, null, null, null, null, null);
        addRecipeItem(32, Material.AIR, null, null, null, null, null);
        addRecipeItem(33, Material.RABBIT_FOOT, null, null, null, null, null);
        addRecipeItem(34, Material.CROSSBOW, null, null, null, null, null);

        addRecipeItem(38, Material.CROSSBOW, "グラップラー", TextColor.color(0xFFD700),
                List.of(Component.text("完成品"), Component.text("40ブロック先まで移動できるぞ！")), Enchantment.PIERCING, 1);
        addRecipeItem(42, Material.CROSSBOW, "レジェンドグラップラー", TextColor.color(0xFF5555),
                List.of(Component.text("完成品"), Component.text("90ブロック先まで移動できるぞ！")), Enchantment.PIERCING, 1);

        addRecipeItem(CLOSE_SLOT, Material.BARRIER, "閉じる", TextColor.color(0xFF5555), null, null, null);
        addRecipeItem(BACK_SLOT, Material.SPECTRAL_ARROW, "メインメニューに戻る", TextColor.color(0xFFD700),null,null,null);
    }

    /**
     * アイテム追加メソッド（DisplayName・色・Lore・エンチャント対応）
     *
     * @param slot スロット番号
     * @param material アイテム種類
     * @param displayName nullならバニラ名、文字列指定で名前を変更
     * @param color DisplayName の色
     * @param lore Lore（Componentのリスト）
     * @param enchant エンチャント（nullならなし）
     * @param level エンチャントレベル
     */
    private void addRecipeItem(int slot, Material material, String displayName, TextColor color,
                               List<Component> lore, Enchantment enchant, Integer level) {
        if (material == Material.AIR) return;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null && displayName != null) {
            Component name = Component.text(displayName);
            if (color != null) name = name.color(color);
            name = name.decoration(TextDecoration.ITALIC, false);
            meta.displayName(name);

            if (lore != null && !lore.isEmpty()) meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            if (enchant != null && level != null) meta.addEnchant(enchant, level, true);
            item.setItemMeta(meta);
        }
        recipeItems.put(slot, item);
    }

    /** GUIを開く */
    public void openRecipePage(Player player, int page) {
        Inventory gui = Bukkit.createInventory(null, 54, "クラフトレシピ");
        for (Map.Entry<Integer, ItemStack> entry : recipeItems.entrySet()) {
            gui.setItem(entry.getKey(), entry.getValue());
        }
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);
        Set<Integer> blankSlots = Set.of(11, 12, 16, 19, 28, 32);

        for (int i = 0; i < gui.getSize(); i++) {
            if (recipeItems.containsKey(i) || blankSlots.contains(i)) continue;
            gui.setItem(i, filler);
        }


        player.openInventory(gui);
    }

    /** クリックイベント */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(Component.text("クラフトレシピ"))) return;

        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT && event.getWhoClicked() instanceof Player player) {
            player.closeInventory();
        }
        if (slot == BACK_SLOT && event.getWhoClicked() instanceof  Player player) {
            player.closeInventory();
            player.performCommand("menu");
        }

        event.setCancelled(true);
    }

    /** ドラッグイベント */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().title().equals(Component.text("レシピGUI"))) event.setCancelled(true);
    }
}