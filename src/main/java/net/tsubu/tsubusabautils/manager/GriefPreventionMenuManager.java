package net.tsubu.tsubusabautils.manager;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.flags.FlagDefinition;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.tsubu.tsubusabautils.TsubusabaUtils;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import me.ryanhamshire.GriefPrevention.ClaimPermission;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class GriefPreventionMenuManager implements Listener {

    private final TsubusabaUtils plugin;
    private final GriefPrevention griefPrevention;
    private final Economy economy;
    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    private final Map<UUID, Integer> pendingPurchaseAmount = new HashMap<>();
    private final Map<UUID, Integer> pendingSellAmount = new HashMap<>();
    private final Map<UUID, String> playerSearchQuery = new HashMap<>();
    private static final long COOLDOWN_MS = 50;
    private static final DecimalFormat df = new DecimalFormat("#,##0.#");
    private final double claimBlockCost;
    private final double sellRate;

    private final List<FlagDisplay> flagsToShow = List.of(
            new FlagDisplay("novinegrowth", "ツタの成長防止", Material.VINE, 24,
                    List.of(Component.text("ツタ系の成長を防ぎます").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))),
            new FlagDisplay("allowpvp", "PvP", Material.IRON_SWORD, 14,
                    List.of(Component.text("PvPの許可を設定できます").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))),
            new FlagDisplay("nomobspawns", "モブのスポーン防止", Material.COW_SPAWN_EGG, 11,
                    List.of(Component.text("土地内でのモブのスポーンを防ぎます").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))),
            new FlagDisplay("nomonsterspawns", "敵モブのスポーン防止", Material.ZOMBIE_SPAWN_EGG, 12,
                    List.of(Component.text("土地内での敵モブのスポーンを防ぎます※外で湧いた敵モブは侵入できます").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))),
            new FlagDisplay("nomonsters", "敵モブ防止", Material.BARRIER, 13,
                    List.of(Component.text("土地内での敵モブのスポーンと攻撃を防ぎます").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))),
            new FlagDisplay("nocroptrampling", "畑荒らし防止", Material.GOLDEN_HOE, 20,
                    List.of(Component.text("農作物が踏み荒らされるのを防ぎます").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))),
            new FlagDisplay("noexplosiondamage", "爆発防止", Material.WITHER_ROSE, 10,
                    List.of(Component.text("クリーパーやTNTの爆発を防ぎます").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))),
            new FlagDisplay("nofiredamage", "火のブロックダメージ防止", Material.FLINT_AND_STEEL, 15,
                    List.of(Component.text("火によるブロックへのダメージを防ぎます").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))),
            new FlagDisplay("nofirespread", "火の延焼防止", Material.LAVA_BUCKET, 16,
                    List.of(Component.text("火の延焼を防ぎます").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))),
            new FlagDisplay("nofalldamage", "落下ダメージ防止", Material.DIAMOND_BOOTS, 19,
                    List.of(Component.text("落下ダメージを防ぎます").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))),
            new FlagDisplay("nofluidflow", "液体の流れ防止", Material.WATER_BUCKET, 21,
                    List.of(Component.text("液体（水など）が流れるのを防ぎます").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))),
            new FlagDisplay("noiceform", "氷の生成防止", Material.ICE, 22,
                    List.of(Component.text("氷の生成を防ぎます").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))),
            new FlagDisplay("noleafdecay", "葉の自然消滅防止", Material.OAK_LEAVES, 23,
                    List.of(Component.text("葉の自然消滅を防ぎます").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))),
            new FlagDisplay("nosnowform", "雪の生成防止", Material.SNOW_BLOCK, 25,
                    List.of(Component.text("雪がつもらなくなります").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)))
    );

    private File claimsFile;
    private FileConfiguration claimsConfig;

    public GriefPreventionMenuManager(TsubusabaUtils plugin, GriefPrevention griefPrevention, Economy economy) {
        this.plugin = plugin;
        this.griefPrevention = griefPrevention;
        this.economy = economy;
        setupClaimsConfig();
        this.claimBlockCost = griefPrevention.getConfig().getDouble("economy.ClaimBlocksPurchaseCost", 3.0);
        this.sellRate = griefPrevention.getConfig().getDouble("economy.ClaimBlocksSellValue", 1.0);
    }

    private void setupClaimsConfig() {
        claimsFile = new File(plugin.getDataFolder(), "claims.yml");
        if (!claimsFile.exists()) {
            try {
                claimsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create claims.yml file: " + e.getMessage());
            }
        }
        claimsConfig = YamlConfiguration.loadConfiguration(claimsFile);
    }

    private void saveClaimsConfig() {
        try {
            claimsConfig.save(claimsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save claims.yml file: " + e.getMessage());
        }
    }

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, Component.text("土地メニュー")
                        .decoration(TextDecoration.BOLD, true).color(NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));

        inv.setItem(4, createPlayerInfoItem(player));

        inv.setItem(10, createMenuItem(Material.REDSTONE_TORCH, "土地保護設定",
                Arrays.asList("現在いる土地の保護を設定できます")));
        inv.setItem(12, createMenuItem(Material.NAME_TAG, "土地名前変更",
                Arrays.asList("現在いる土地の名前を変更します")));
        inv.setItem(28, createMenuItem(Material.PLAYER_HEAD, "プレイヤー追加/管理",
                Arrays.asList("現在の土地にプレイヤーを追加したり、", "権限を管理します")));
        inv.setItem(30, createMenuItemWithCustomLore(Material.GRASS_BLOCK, "土地一覧",
                Arrays.asList(
                        Component.text("自分が所有/所属している土地を").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                        Component.text("一覧で表示します").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                        Component.text("これは実験的機能です").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
                )));
        inv.setItem(14, createMenuItem(Material.EMERALD_BLOCK, "保護ブロック数購入",
                Arrays.asList("保護ブロックを購入します")));
        inv.setItem(16, createMenuItem(Material.RED_CONCRETE, "土地放棄",
                Arrays.asList("現在いる土地を放棄します")));
        inv.setItem(32, createMenuItem(Material.IRON_BLOCK, "保護ブロック数売却",
                Arrays.asList("保護ブロックをお金に交換します")));
        inv.setItem(36, createMenuItem(Material.SPECTRAL_ARROW, "メインメニューに戻る",
                Arrays.asList("メインメニューに戻ります")));
        inv.setItem(40, createMenuItem(Material.BARRIER, "閉じる",
                Arrays.asList("メニューを閉じます")));

        player.openInventory(inv);
    }

    public void openPlayerListMenu(Player player, int page) {
        openPlayerListMenu(player, page, null);
    }

    private void openPlayerListMenu(Player player, int page, String searchQuery) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<OfflinePlayer> onlinePlayers = new ArrayList<>();
            List<OfflinePlayer> offlinePlayers = new ArrayList<>();

            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op.isBanned()) continue;
                if (op.getUniqueId().equals(player.getUniqueId())) continue;

                if (searchQuery != null && !searchQuery.isEmpty()) {
                    String name = op.getName();
                    if (name == null || !name.toLowerCase().contains(searchQuery.toLowerCase())) {
                        continue;
                    }
                }

                if (op.isOnline()) onlinePlayers.add(op);
                else offlinePlayers.add(op);
            }

            List<OfflinePlayer> sortedPlayers = new ArrayList<>();
            sortedPlayers.addAll(onlinePlayers);
            sortedPlayers.addAll(offlinePlayers);

            Bukkit.getScheduler().runTask(plugin, () -> {
                openPlayerListMenuSync(player, page, searchQuery, sortedPlayers);
            });
        });
    }

    private void openPlayerListMenuSync(Player player, int page, String searchQuery, List<OfflinePlayer> sortedPlayers) {
        int pageSize = 45;
        int totalPlayers = sortedPlayers.size();
        int totalPages = (int) Math.ceil((double) totalPlayers / pageSize);

        if (page < 0) page = 0;
        if (page >= totalPages && totalPages > 0) page = totalPages - 1;

        int start = page * pageSize;
        int end = Math.min(start + pageSize, totalPlayers);

        String title = searchQuery != null && !searchQuery.isEmpty()
                ? "検索: " + searchQuery + " - ページ " + (page + 1) + "/" + Math.max(1, totalPages)
                : "プレイヤー一覧 - ページ " + (page + 1) + "/" + Math.max(1, totalPages);

        Inventory gui = Bukkit.createInventory(null, 54,
                Component.text(title)
                        .color(NamedTextColor.BLUE)
                        .decorate(TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));

        for (int i = start; i < end; i++) {
            OfflinePlayer target = sortedPlayers.get(i);
            gui.setItem(i - start, createPlayerHeadItemFull(target));
        }

        ItemStack searchItem = new ItemStack(Material.SPYGLASS);
        searchItem.editMeta(meta -> {
            meta.displayName(Component.text("プレイヤー検索")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            if (searchQuery != null && !searchQuery.isEmpty()) {
                lore.add(Component.text("現在の検索: " + searchQuery)
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("クリックで検索をクリア")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("クリックして名前で検索")
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        });
        gui.setItem(49, searchItem);

        gui.setItem(45, createMenuItem(Material.ARROW, "戻る", Arrays.asList("土地メニューに戻る")));
        if (page > 0) {
            gui.setItem(48, createMenuItem(Material.ORANGE_DYE, "◀ 前のページ", Arrays.asList("前のページへ")));
        }
        if (page < totalPages - 1) {
            gui.setItem(50, createMenuItem(Material.LIME_DYE, "次のページ ▶", Arrays.asList("次のページへ")));
        }
        gui.setItem(53, createMenuItem(Material.BARRIER, "閉じる", Arrays.asList("メニューを閉じます")));

        player.openInventory(gui);
    }

    private void openPlayerSearchInput(Player player) {
        new AnvilGUI.Builder()
                .onClick((slot, stateSnapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) {
                        return Collections.emptyList();
                    }

                    String searchQuery = stateSnapshot.getText();
                    if (searchQuery == null || searchQuery.trim().isEmpty()) {
                        return List.of(AnvilGUI.ResponseAction.replaceInputText("名前を入力"));
                    }

                    String query = searchQuery.trim();
                    playerSearchQuery.put(player.getUniqueId(), query);
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        openPlayerListMenu(player, 0, query);
                    });

                    return List.of(AnvilGUI.ResponseAction.close());
                })
                .text("")
                .title("プレイヤー検索")
                .plugin(plugin)
                .open(player);
    }

    private void openPermissionMenu(Player player, OfflinePlayer target) {
        String title = "権限設定: " + target.getName();
        Inventory menu = Bukkit.createInventory(null, 27, Component.text(title)
                .color(NamedTextColor.DARK_AQUA)
                .decoration(TextDecoration.ITALIC, false));

        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
        com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(target.getUniqueId(), target.getName());
        profile.complete();
        skullMeta.setPlayerProfile(profile);
        playerHead.setItemMeta(skullMeta);
        menu.setItem(4, playerHead);

        Claim claim = this.griefPrevention.dataStore.getClaimAt(player.getLocation(), false, null);
        if (claim == null) {
            player.sendMessage(Component.text("ここは保護されていない土地です！").color(NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        String targetUUID = target.getUniqueId().toString();
        ClaimPermission currentPermission = claim.getPermission(targetUUID);
        boolean hasAccess = (currentPermission == ClaimPermission.Access);
        boolean hasInventory = (currentPermission == ClaimPermission.Inventory);
        boolean hasBuild = (currentPermission == ClaimPermission.Build);

        menu.setItem(11, createPermissionItem(
                LegacyComponentSerializer.legacyAmpersand().serialize(Component.text("土地の訪問者").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD)),
                hasAccess,
                Arrays.asList(
                        Component.text("土地内でレバーやドアなどを操作できる").color(NamedTextColor.YELLOW),
                        Component.text("").color(NamedTextColor.WHITE),
                        Component.text("クリック/タップで権限を付与/解除").color(NamedTextColor.AQUA)
                )
        ));

        menu.setItem(13, createPermissionItem(
                LegacyComponentSerializer.legacyAmpersand().serialize(Component.text("土地の利用者").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)),
                hasInventory,
                Arrays.asList(
                        Component.text("訪問者権限に加え、土地内のチェスト・かまど・樽などを使用できる").color(NamedTextColor.YELLOW),
                        Component.text("").color(NamedTextColor.WHITE),
                        Component.text("クリック/タップで権限を付与/解除").color(NamedTextColor.AQUA)
                )
        ));

        menu.setItem(15, createPermissionItem(
                LegacyComponentSerializer.legacyAmpersand().serialize(Component.text("土地の建築者").color(NamedTextColor.RED).decorate(TextDecoration.BOLD)),
                hasBuild,
                Arrays.asList(
                        Component.text("利用者権限に加え、土地内でブロックの設置/破壊ができる").color(NamedTextColor.YELLOW),
                        Component.text("").color(NamedTextColor.WHITE),
                        Component.text("クリック/タップで権限を付与/解除").color(NamedTextColor.AQUA)
                )
        ));

        boolean isOwner = claim.getOwnerID() != null && claim.getOwnerID().equals(player.getUniqueId());
        boolean isMember = currentPermission != null;

        if (isOwner && isMember) {
            ItemStack banItem = new ItemStack(Material.RED_DYE);
            ItemMeta banMeta = banItem.getItemMeta();
            banMeta.displayName(Component.text("追放")
                    .color(NamedTextColor.RED)
                    .decorate(TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            banItem.setItemMeta(banMeta);
            menu.setItem(22, banItem);
        }

        menu.setItem(18, createMenuItem(Material.ARROW, "戻る", Arrays.asList("土地メニューに戻る")));
        menu.setItem(26, createMenuItem(Material.BARRIER, "閉じる", Arrays.asList("メニューを閉じます")));

        player.openInventory(menu);
    }

    public void openAbandonClaimMenu(Player player) {
        Claim claim = griefPrevention.dataStore.getClaimAt(player.getLocation(), false, null);
        if (claim == null) {
            player.sendMessage(Component.text("ここは保護されていない土地です！").color(NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        if (!claim.getOwnerID().equals(player.getUniqueId()) && !player.hasPermission("griefprevention.adminclaims")) {
            player.sendMessage(Component.text("この土地を放棄する権限がありません！").color(NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 27,
                Component.text("土地放棄確認").color(NamedTextColor.RED).decorate(TextDecoration.BOLD));

        ItemStack landItem = new ItemStack(Material.GRASS_BLOCK);
        landItem.editMeta(meta -> {
            meta.displayName(Component.text("土地を放棄しますか？")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD,true)
            );
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(getClaimName(claim))
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false)
            );
            meta.lore(lore);
        });
        gui.setItem(4, landItem);

        gui.setItem(11, createMenuItem(Material.RED_CONCRETE, "はい", Arrays.asList("この土地を放棄します")));
        gui.setItem(15, createMenuItem(Material.GREEN_CONCRETE, "いいえ", Arrays.asList("土地メニューに戻る")));
        gui.setItem(22, createMenuItem(Material.BARRIER, "閉じる", Arrays.asList("メニューを閉じます")));

        player.openInventory(gui);
    }

    public void openBlockPurchaseMenu(Player player, int amount) {
        Inventory gui = Bukkit.createInventory(null, 36,
                Component.text("保護ブロック数購入")
                        .color(NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));

        gui.setItem(16, createPurchaseButton(player, amount));
        gui.setItem(10, createButton(Material.GREEN_STAINED_GLASS_PANE, "+1"));
        gui.setItem(11, createButton(Material.GREEN_STAINED_GLASS_PANE, "+10"));
        gui.setItem(12, createButton(Material.GREEN_STAINED_GLASS_PANE, "+100"));
        gui.setItem(13, createButton(Material.GREEN_STAINED_GLASS_PANE, "+1000"));
        gui.setItem(14, createButton(Material.GREEN_STAINED_GLASS_PANE, "+10000"));
        gui.setItem(19, createButton(Material.RED_STAINED_GLASS_PANE, "-1"));
        gui.setItem(20, createButton(Material.RED_STAINED_GLASS_PANE, "-10"));
        gui.setItem(21, createButton(Material.RED_STAINED_GLASS_PANE, "-100"));
        gui.setItem(22, createButton(Material.RED_STAINED_GLASS_PANE, "-1000"));
        gui.setItem(23, createButton(Material.RED_STAINED_GLASS_PANE, "-10000"));

        gui.setItem(27, createMenuItem(Material.ARROW, "戻る", Arrays.asList("土地メニューに戻る")));
        gui.setItem(35, createMenuItem(Material.BARRIER, "閉じる", Arrays.asList("メニューを閉じます")));

        player.openInventory(gui);
    }

    public void openBlockSellMenu(Player player, int amount) {
        Inventory gui = Bukkit.createInventory(null, 36,
                Component.text("保護ブロック数売却")
                        .color(NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));

        gui.setItem(16, createSellButton(player, amount));
        gui.setItem(10, createButton(Material.GREEN_STAINED_GLASS_PANE, "+1"));
        gui.setItem(11, createButton(Material.GREEN_STAINED_GLASS_PANE, "+10"));
        gui.setItem(12, createButton(Material.GREEN_STAINED_GLASS_PANE, "+100"));
        gui.setItem(13, createButton(Material.GREEN_STAINED_GLASS_PANE, "+1000"));
        gui.setItem(14, createButton(Material.GREEN_STAINED_GLASS_PANE, "+10000"));
        gui.setItem(19, createButton(Material.RED_STAINED_GLASS_PANE, "-1"));
        gui.setItem(20, createButton(Material.RED_STAINED_GLASS_PANE, "-10"));
        gui.setItem(21, createButton(Material.RED_STAINED_GLASS_PANE, "-100"));
        gui.setItem(22, createButton(Material.RED_STAINED_GLASS_PANE, "-1000"));
        gui.setItem(23, createButton(Material.RED_STAINED_GLASS_PANE, "-10000"));

        gui.setItem(27, createMenuItem(Material.ARROW, "戻る", Arrays.asList("土地メニューに戻る")));
        gui.setItem(35, createMenuItem(Material.BARRIER, "閉じる", Arrays.asList("メニューを閉じます")));

        player.openInventory(gui);
    }

    private void purchaseClaimBlocks(Player player, int amount, double price) {
        if (economy.has(player, price)) {
            economy.withdrawPlayer(player, price);
            PlayerData playerData = griefPrevention.dataStore.getPlayerData(player.getUniqueId());
            playerData.setAccruedClaimBlocks(playerData.getAccruedClaimBlocks() + amount);
            griefPrevention.dataStore.savePlayerData(player.getUniqueId(), playerData);
            player.sendMessage(Component.text("保護ブロックを " + df.format(amount) + " 個購入しました！").color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("現在の所持金: " + df.format(economy.getBalance(player)) + "D").color(NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            player.closeInventory();
        } else {
            player.sendMessage(Component.text("所持金が不足しています").color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            openBlockPurchaseMenu(player, amount);
        }
    }

    private void sellClaimBlocks(Player player, int amount) {
        PlayerData playerData = griefPrevention.dataStore.getPlayerData(player.getUniqueId());
        int remainingBlocks = playerData.getRemainingClaimBlocks();

        if (amount <= 0) {
            player.sendMessage(Component.text("売却ブロック数は1以上にしてください！").color(NamedTextColor.RED));
            return;
        }

        if (amount > remainingBlocks) {
            player.sendMessage(Component.text("所持ブロック数を超えています！").color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            openBlockSellMenu(player,amount);
            return;
        }

        double sellValue = griefPrevention.getConfig().getDouble("economy.ClaimBlocksSellValue", 1.0);
        double price = amount * sellValue;
        playerData.setBonusClaimBlocks(playerData.getBonusClaimBlocks() - amount);
        griefPrevention.dataStore.savePlayerData(player.getUniqueId(), playerData);
        economy.depositPlayer(player, price);

        player.sendMessage(Component.text(amount + "個の保護ブロックを売却しました！").color(NamedTextColor.GREEN));
        player.sendMessage(Component.text("現在の所持金: " + df.format(economy.getBalance(player)) + "D").color(NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        player.closeInventory();
    }

    private ItemStack createMenuItem(Material material, String name, List<String> lore, NamedTextColor nameColor) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(name)
                    .color(nameColor)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(Component.text(line)
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(loreComponents);

            if (material.equals(Material.BARRIER)) {
                meta.addEnchant(Enchantment.INFINITY, 1, false);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        });
        return item;
    }

    private ItemStack createMenuItem(Material material, String name, List<String> lore) {
        return createMenuItem(material, name, lore, NamedTextColor.YELLOW);
    }

    private ItemStack createPlayerHeadItemFull(OfflinePlayer target) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(target.getUniqueId(), target.getName());
        profile.complete();

        meta.setPlayerProfile(profile);
        meta.displayName(Component.text(target.getName() != null ? target.getName() : "不明")
                .color(target.isOnline() ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = Arrays.asList(
                Component.text("クリック/タップで追加・管理")
                        .color(NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text(target.isOnline() ? "オンライン" : "オフライン")
                        .color(target.isOnline() ? NamedTextColor.GREEN : NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false)
        );
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPermissionItem(String name, boolean granted, List<Component> lore) {
        Material material = granted ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        String status = granted ? "✓" : "✗";
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(name + " (" + status + ")")
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> newLore = new ArrayList<>();
            for (Component line : lore) {
                newLore.add(line.decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(newLore);
        });
        return item;
    }

    private ItemStack createSellButton(Player player, int amount) {
        PlayerData playerData = griefPrevention.dataStore.getPlayerData(player.getUniqueId());
        int remainingBlocks = playerData.getRemainingClaimBlocks();

        ItemStack item = new ItemStack(Material.IRON_BLOCK);
        item.editMeta(meta -> {
            meta.displayName(Component.text("売却確定")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();

            double sellValue = griefPrevention.getConfig().getDouble("economy.ClaimBlocksSellValue", 1.0);
            double price = amount * sellValue;

            lore.add(Component.text("合計: " + amount + "個")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("売却額: " + df.format(price) + "D")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("所持ブロック数: " + remainingBlocks)
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("クリック/タップで売却")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));

            if (amount <= 0) {
                lore.add(Component.text("売却数は1以上にしてください！")
                        .color(NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
            } else if (amount > remainingBlocks) {
                lore.add(Component.text("所持ブロック数を超えています！")
                        .color(NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
        });
        return item;
    }

    private ItemStack createPurchaseButton(Player player, int amount) {
        ItemStack item = new ItemStack(Material.EMERALD_BLOCK);
        item.editMeta(meta -> {
            meta.displayName(Component.text("購入確定")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();

            double price = amount * claimBlockCost;
            lore.add(Component.text("合計: " + amount + "個")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("価格: " + df.format(price) + "D")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));

            double balance = economy.getBalance(player);
            lore.add(Component.text("残高: " + df.format(balance) + "D")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("クリック/タップで購入")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));

            if (amount <= 0) {
                lore.add(Component.text("購入数は1以上にしてください！")
                        .color(NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
            } else if (balance < price) {
                lore.add(Component.text("所持金が不足しています")
                        .color(NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
        });
        return item;
    }

    private ItemStack createMenuItemWithCustomLore(Material material, String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(name)
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        return item;
    }

    private ItemStack createButton(Material material, String name) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(name)
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        });
        return item;
    }

    private ItemStack createPlayerInfoItem(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        item.editMeta(meta -> {
            if (meta instanceof SkullMeta) {
                SkullMeta skullMeta = (SkullMeta) meta;
                skullMeta.setOwningPlayer(player);
                skullMeta.displayName(Component.text(player.getName())
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));

                Economy economy = TsubusabaUtils.getEconomy();
                PlayerData playerData = this.griefPrevention.dataStore.getPlayerData(player.getUniqueId());

                List<Component> lore = new ArrayList<>();
                if (economy != null) {
                    lore.add(Component.text("所持金: " + df.format(economy.getBalance(player)) + "D")
                            .color(NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false));
                }

                if (playerData != null) {
                    int totalBlocks = playerData.getAccruedClaimBlocks() + playerData.getBonusClaimBlocks();
                    lore.add(Component.text("保護ブロック: " + totalBlocks)
                            .color(NamedTextColor.BLUE)
                            .decoration(TextDecoration.ITALIC, false));

                    int remaining = playerData.getRemainingClaimBlocks();
                    int used = Math.max(0, totalBlocks - remaining);
                    lore.add(Component.text("使用中: " + used)
                            .color(NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("残り: " + remaining)
                            .color(NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(lore);
            }
        });
        return item;
    }

    public void openClaimListMenu(Player player, int page) {
        PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());

        List<Claim> ownedClaims = playerData.getClaims().stream()
                .filter(claim -> claim.parent == null)
                .collect(Collectors.toList());

        List<Claim> accessibleClaims = new ArrayList<>();
        String playerIdString = player.getUniqueId().toString();

        for (Claim claim : GriefPrevention.instance.dataStore.getClaims()) {
            if (claim.parent != null) continue;
            if (claim.getOwnerID() != null && claim.getOwnerID().equals(player.getUniqueId())) continue;

            ClaimPermission permission = claim.getPermission(playerIdString);
            if (permission != null) {
                accessibleClaims.add(claim);
            }
        }

        List<Claim> allClaims = new ArrayList<>();
        allClaims.addAll(ownedClaims);
        allClaims.addAll(accessibleClaims);

        int pageSize = 45;
        int totalClaims = allClaims.size();
        int totalPages = (int) Math.ceil((double) totalClaims / pageSize);

        if (page < 0) page = 0;
        if (page >= totalPages && totalPages > 0) page = totalPages - 1;

        int start = page * pageSize;
        int end = Math.min(start + pageSize, totalClaims);

        Inventory gui = Bukkit.createInventory(null, 54,
                Component.text("土地一覧 - ページ " + (page + 1) + "/" + Math.max(1, totalPages))
                        .color(NamedTextColor.AQUA)
                        .decorate(TextDecoration.BOLD));

        for (int i = start; i < end; i++) {
            Claim claim = allClaims.get(i);
            gui.setItem(i - start, createClaimItem(claim, player));
        }

        gui.setItem(45, createMenuItem(Material.ARROW, "戻る", Arrays.asList("土地メニューに戻る")));
        if (page > 0) {
            gui.setItem(48, createMenuItem(Material.ORANGE_DYE, "◀ 前のページ", Arrays.asList("前のページへ")));
        }
        if (page < totalPages - 1) {
            gui.setItem(50, createMenuItem(Material.LIME_DYE, "次のページ ▶", Arrays.asList("次のページへ")));
        }
        gui.setItem(53, createMenuItem(Material.BARRIER, "閉じる", Arrays.asList("メニューを閉じます")));

        player.openInventory(gui);
    }

    public void openRenameAnvil(Player player) {
        Claim claim = this.griefPrevention.dataStore.getClaimAt(player.getLocation(), false, null);

        if (claim == null) {
            player.sendMessage(Component.text("ここは保護されていない土地です！").color(NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        if (claim.allowEdit(player) != null && !player.hasPermission("griefprevention.adminclaims")) {
            player.sendMessage(Component.text("この土地の編集権限がありません！").color(NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        String currentName = getClaimName(claim);

        new AnvilGUI.Builder()
                .onClick((slot, stateSnapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) {
                        return Collections.emptyList();
                    }

                    String newName = stateSnapshot.getText();
                    if (newName != null && !newName.trim().isEmpty()) {
                        setClaimName(claim, newName.trim());
                        player.sendMessage(Component.text("土地名を「" + newName.trim() + "」に変更しました！")
                                .color(NamedTextColor.GREEN));
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                        return List.of(AnvilGUI.ResponseAction.close());
                    }
                    return List.of(AnvilGUI.ResponseAction.replaceInputText("名前を入力してください"));
                })
                .text(currentName)
                .title("土地名を入力")
                .plugin(plugin)
                .open(player);
    }

    private void openFlagMenu(Player player) {
        Claim claim = griefPrevention.dataStore.getClaimAt(player.getLocation(), false, null);
        if (claim == null) {
            player.sendMessage(Component.text("ここは保護されていない土地です！").color(NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        if (!claim.getOwnerID().equals(player.getUniqueId()) && !player.hasPermission("griefprevention.adminclaims")) {
            player.sendMessage(Component.text("この土地の設定権限がありません！").color(NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        GPFlags gpFlags = (GPFlags) Bukkit.getPluginManager().getPlugin("GPFlags");
        if (gpFlags == null) {
            player.sendMessage(Component.text("GPFlags プラグインが見つかりません。").color(NamedTextColor.RED));
            return;
        }

        FlagManager flagManager = gpFlags.getFlagManager();
        Inventory gui = Bukkit.createInventory(null, 36, Component.text("土地保護設定")
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD));

        ItemStack landItem = new ItemStack(Material.GRASS_BLOCK);
        landItem.editMeta(landMeta -> {
            landMeta.displayName(Component.text(getClaimName(claim))
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        });
        gui.setItem(4, landItem);


        for (FlagDisplay fd : flagsToShow) {
            FlagDefinition def = flagManager.getFlagDefinitions().stream()
                    .filter(d -> d.getName().equalsIgnoreCase(fd.id))
                    .findFirst()
                    .orElse(null);
            if (def == null) continue;

            boolean isSet = flagManager.getFlags(claim).stream()
                    .filter(f -> f.getFlagDefinition().equals(def))
                    .findFirst()
                    .map(Flag::getSet)
                    .orElse(false);

            ItemStack item = new ItemStack(fd.material);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            meta.displayName(Component.text(fd.displayName)
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>(fd.lore);
            lore.add(Component.text("現在の状態: " + (isSet ? "有効" : "無効"))
                    .color(isSet ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("クリック/タップで切替")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(lore);

            if (isSet) {
                meta.addEnchant(Enchantment.INFINITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                meta.getEnchants().keySet().forEach(meta::removeEnchant);
            }

            item.setItemMeta(meta);
            gui.setItem(fd.slot, item);
        }

        gui.setItem(27, createMenuItem(Material.ARROW, "戻る", List.of("土地メニューに戻る")));
        gui.setItem(35, createMenuItem(Material.BARRIER, "閉じる", List.of("メニューを閉じます")));

        player.openInventory(gui);
    }

    private ItemStack createClaimItem(Claim claim, Player player) {
        ItemStack item = new ItemStack(Material.GRASS_BLOCK);
        item.editMeta(meta -> {
            String name = getClaimName(claim);

            boolean isOwner = claim.getOwnerID() != null && claim.getOwnerID().equals(player.getUniqueId());
            String ownerName = "";

            OfflinePlayer owner = null;
            if (claim.getOwnerID() != null) {
                owner = Bukkit.getOfflinePlayer(claim.getOwnerID());
                ownerName = owner.getName() != null ? owner.getName() : "不明";
            }

            NamedTextColor titleColor = isOwner ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
            meta.displayName(Component.text(name)
                    .color(titleColor)
                    .decoration(TextDecoration.ITALIC, false));

            int area = (int) ((claim.getGreaterBoundaryCorner().getX() - claim.getLesserBoundaryCorner().getX() + 1)
                    * (claim.getGreaterBoundaryCorner().getZ() - claim.getLesserBoundaryCorner().getZ() + 1));

            int centerX = (int) ((claim.getLesserBoundaryCorner().getX() + claim.getGreaterBoundaryCorner().getX()) / 2);
            int centerZ = (int) ((claim.getLesserBoundaryCorner().getZ() + claim.getGreaterBoundaryCorner().getZ()) / 2);
            int centerY = claim.getLesserBoundaryCorner().getWorld().getHighestBlockYAt(centerX, centerZ);

            Location center = new Location(claim.getLesserBoundaryCorner().getWorld(),
                    centerX,
                    centerY,
                    centerZ);

            List<Component> lore = new ArrayList<>();

            if (isOwner) {
                lore.add(Component.text("自分の土地")
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            } else if (owner != null) {
                lore.add(Component.text("所有者: " + ownerName)
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("所有者: 不明")
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            }

            String permissionText;
            NamedTextColor permissionColor;

            if (isOwner) {
                permissionText = "オーナー";
                permissionColor = NamedTextColor.GOLD;
            } else {
                ClaimPermission permission = claim.getPermission(player.getUniqueId().toString());
                if (permission != null) {
                    switch (permission) {
                        case Access:
                            permissionText = "訪問者";
                            permissionColor = NamedTextColor.YELLOW;
                            break;
                        case Inventory:
                            permissionText = "利用者";
                            permissionColor = NamedTextColor.GOLD;
                            break;
                        case Build:
                            permissionText = "建築者";
                            permissionColor = NamedTextColor.RED;
                            break;
                        default:
                            permissionText = "不明";
                            permissionColor = NamedTextColor.GRAY;
                            break;
                    }
                } else {
                    permissionText = "不明";
                    permissionColor = NamedTextColor.GRAY;
                }
            }

            lore.add(Component.text("権限: " + permissionText)
                    .color(permissionColor)
                    .decoration(TextDecoration.ITALIC, false));

            lore.add(Component.text("面積: " + df.format(area) + "ブロック")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));

            String worldName = claim.getLesserBoundaryCorner().getWorld().getName();
            lore.add(Component.text("ワールド: " + worldName)
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));

            lore.add(Component.text("座標: X:" + center.getBlockX() + ", Y:" + center.getBlockY() + ", Z:" + center.getBlockZ())
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("クリック/タップでテレポート")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(lore);
        });
        return item;
    }

    private String getClaimName(Claim claim) {
        String name = claimsConfig.getString("claims." + claim.getID() + ".name");
        return name != null ? name : "土地 #" + claim.getID();
    }

    private void setClaimName(Claim claim, String name) {
        claimsConfig.set("claims." + claim.getID() + ".name", name);
        saveClaimsConfig();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) return;

        Component titleComponent = player.getOpenInventory().title();
        if (titleComponent == null) return;

        String titleText = PlainTextComponentSerializer.plainText().serialize(titleComponent);

        if (titleText.contains("土地メニュー") || titleText.contains("プレイヤー一覧") ||
                titleText.contains("権限設定") || titleText.contains("土地一覧") || titleText.equals("保護ブロック数購入") ||
                titleText.equals("土地保護設定") || titleText.equals("土地放棄確認") || titleText.equals("保護ブロック数売却") ||
                titleText.contains("検索:")
        ) {

            if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP || event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                event.setCancelled(true);
                return;
            }
            event.setCancelled(true);
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        long currentTime = System.currentTimeMillis();
        if (lastClickTime.containsKey(player.getUniqueId()) &&
                currentTime - lastClickTime.get(player.getUniqueId()) < COOLDOWN_MS) {
            return;
        }
        lastClickTime.put(player.getUniqueId(), currentTime);

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.displayName() == null) return;

        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        if (titleText.equals("土地メニュー")) {
            handleMainMenuClick(player, itemName);
        } else if (titleText.equals("プレイヤー一覧") || titleText.contains("プレイヤー一覧 - ページ ") || titleText.contains("検索:")) {
        handlePlayerListClick(player, itemName, clicked, titleText);
        } else if (titleText.startsWith("権限設定:")) {
            handlePermissionMenuClick(player, titleText, itemName);
        } else if (titleText.equals("保護ブロック数購入")) {
            handleBlockPurchaseClick(player, itemName);
        } else if (titleText.contains("土地一覧 - ページ ")) {
            handleClaimListClick(player, itemName, clicked, titleText);
        } else if (titleText.equals("土地保護設定")) {
            handleFlagMenuClick(player, clicked);
        } else if (titleText.equals("土地放棄確認")) {
            handleAbandonClaimMenuClick(player, clicked);
        } else if (titleText.equals("保護ブロック数売却")) {
            handleBlockSellClick(player, itemName);
        }
    }

    private void handleMainMenuClick(Player player, String itemName) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        switch (itemName) {
            case "土地保護設定" -> openFlagMenu(player);
            case "土地名前変更" -> openRenameAnvil(player);
            case "プレイヤー追加/管理" -> openPlayerListMenu(player, 0);
            case "土地一覧" -> openClaimListMenu(player, 0);
            case "保護ブロック数購入" -> {
                pendingPurchaseAmount.put(player.getUniqueId(), 0);
                openBlockPurchaseMenu(player, 0);
            }
            case "土地放棄" -> openAbandonClaimMenu(player);
            case "保護ブロック数売却" -> {
                pendingSellAmount.put(player.getUniqueId(), 0);
                openBlockSellMenu(player, 0);
            }
            case "閉じる" -> player.closeInventory();
            case "メインメニューに戻る" -> {
                player.closeInventory();
                player.performCommand("menu");
            }
        }
    }

    private void handleAbandonClaimMenuClick(Player player, ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) return;
        String clickedName = PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(clicked.getItemMeta().displayName()));

        Claim claim = griefPrevention.dataStore.getClaimAt(player.getLocation(), false, null);
        if (claim == null) {
            player.sendMessage(Component.text("ここは保護されていない土地です！").color(NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        switch (clickedName) {
            case "はい" -> {
                griefPrevention.dataStore.deleteClaim(claim);
                player.sendMessage(Component.text("土地を放棄しました！").color(NamedTextColor.GREEN));
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
                player.closeInventory();
            }
            case "いいえ" -> openMainMenu(player);
            case "閉じる" -> player.closeInventory();
        }
    }

    private void handleFlagMenuClick(Player player, ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) return;

        String clickedName = PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(clicked.getItemMeta().displayName()));

        if (clickedName.equalsIgnoreCase("戻る")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openMainMenu(player);
            return;
        }
        if (clickedName.equalsIgnoreCase("閉じる")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            player.closeInventory();
            return;
        }

        Claim claim = griefPrevention.dataStore.getClaimAt(player.getLocation(), false, null);
        if (claim == null) {
            player.sendMessage(Component.text("ここは保護されていない土地です！").color(NamedTextColor.RED));
            player.closeInventory();
            return;
        }
        String claimName = getClaimName(claim);
        if (clickedName.equals(claimName)) {
            return;
        }

        GPFlags gpFlags = (GPFlags) Bukkit.getPluginManager().getPlugin("GPFlags");
        FlagManager flagManager = gpFlags.getFlagManager();

        String flagId = flagsToShow.stream()
                .filter(fd -> fd.displayName.equals(clickedName))
                .map(fd -> fd.id)
                .findFirst()
                .orElse(null);

        if (flagId == null) {
            player.sendMessage(Component.text("フラグが見つかりません: " + clickedName).color(NamedTextColor.RED));
            return;
        }

        FlagDefinition def = flagManager.getFlagDefinitions().stream()
                .filter(d -> d.getName().equalsIgnoreCase(flagId))
                .findFirst()
                .orElse(null);

        if (def == null) {
            player.sendMessage(Component.text("フラグ定義が見つかりません: " + flagId).color(NamedTextColor.RED));
            return;
        }

        Flag currentFlag = flagManager.getFlags(claim).stream()
                .filter(f -> f.getFlagDefinition().equals(def))
                .findFirst()
                .orElse(null);

        boolean newState = !(currentFlag != null && currentFlag.getSet());

        flagManager.setFlag(claim.getID().toString(), def, newState, player);
        flagManager.save();

        player.sendMessage(Component.text(clickedName + " を " + (newState ? "有効" : "無効") + " にしました").color(NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        openFlagMenu(player);
    }

    private static class FlagDisplay {
        public final String id;
        public final String displayName;
        public final Material material;
        public final int slot;
        public final List<Component> lore;

        public FlagDisplay(String id, String displayName, Material material, int slot, List<Component> lore) {
            this.id = id;
            this.displayName = displayName;
            this.material = material;
            this.slot = slot;
            this.lore = lore;
        }
    }

    private void handlePlayerListClick(Player player, String itemName, ItemStack clicked, String titleText) {
        if (itemName.equals("戻る")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            playerSearchQuery.remove(player.getUniqueId());
            openMainMenu(player);
            return;
        }

        if (itemName.equals("閉じる")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            playerSearchQuery.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }

        if (itemName.equals("プレイヤー検索")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

            String currentQuery = playerSearchQuery.get(player.getUniqueId());
            if (currentQuery != null && !currentQuery.isEmpty()) {
                playerSearchQuery.remove(player.getUniqueId());
                openPlayerListMenu(player, 0, null);
            } else {
                openPlayerSearchInput(player);
            }
            return;
        }

        if (titleText.contains("プレイヤー一覧 - ページ ") || titleText.contains("検索:")) {
            String searchQuery = playerSearchQuery.get(player.getUniqueId());
            String pageInfo = titleText.split("ページ ")[1];
            int currentPage = Integer.parseInt(pageInfo.split("/")[0]);

            if (itemName.equals("◀ 前のページ")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                openPlayerListMenu(player, currentPage - 2, searchQuery);
                return;
            } else if (itemName.equals("次のページ ▶")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                openPlayerListMenu(player, currentPage, searchQuery);
                return;
            }
        }

        if (clicked.getType() == Material.PLAYER_HEAD) {
            String targetName = PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().displayName());
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            if (target != null) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                openPermissionMenu(player, target);
            }
        }
    }

    private void handlePermissionMenuClick(Player player, String titleText, String itemName) {
        if (itemName.equals("戻る")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openPlayerListMenu(player, 0);
            return;
        }

        if (itemName.equals("閉じる")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            player.closeInventory();
            return;
        }

        String targetName = titleText.replace("権限設定: ", "");
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (target == null || target.getName() == null) {
            player.sendMessage(Component.text("対象プレイヤーが見つかりません！").color(NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        Claim claim = this.griefPrevention.dataStore.getClaimAt(player.getLocation(), false, null);
        if (claim == null) {
            player.sendMessage(Component.text("ここは保護されていない土地です！").color(NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        String allowGrantResult = claim.allowGrantPermission(player);
        if (allowGrantResult != null && !player.hasPermission("griefprevention.adminclaims")) {
            player.sendMessage(Component.text("この土地に対する権限がありません！").color(NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);

        if (itemName.equals("追放")) {
            claim.dropPermission(target.getUniqueId().toString());
            player.sendMessage(Component.text(target.getName() + " を土地から追放しました。").color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            griefPrevention.dataStore.saveClaim(claim);
            player.closeInventory();
            return;
        }

        String cleanedItemName = itemName.replace(" (✓)", "").replace(" (✗)", "");
        ClaimPermission permissionToToggle;
        String message;

        switch (cleanedItemName) {
            case "土地の訪問者":
                permissionToToggle = ClaimPermission.Access;
                message = "訪問者";
                break;
            case "土地の利用者":
                permissionToToggle = ClaimPermission.Inventory;
                message = "利用者";
                break;
            case "土地の建築者":
                permissionToToggle = ClaimPermission.Build;
                message = "建築者";
                break;
            default:
                player.sendMessage(Component.text("不明な権限タイプです。").color(NamedTextColor.RED));
                return;
        }

        ClaimPermission currentPermission = claim.getPermission(target.getUniqueId().toString());
        if (currentPermission != null && currentPermission.ordinal() == permissionToToggle.ordinal()) {
            claim.dropPermission(target.getUniqueId().toString());
            player.sendMessage(Component.text(target.getName() + " の" + message + "権限を解除しました。").color(NamedTextColor.RED));
        } else {
            claim.setPermission(target.getUniqueId().toString(), permissionToToggle);
            player.sendMessage(Component.text(target.getName() + " に " + message + " 権限を付与しました。").color(NamedTextColor.GREEN));
        }

        griefPrevention.dataStore.saveClaim(claim);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        player.closeInventory();
    }

    private void handleBlockPurchaseClick(Player player, String itemName) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        int currentAmount = pendingPurchaseAmount.getOrDefault(player.getUniqueId(), 0);
        int newAmount = currentAmount;

        switch (itemName) {
            case "+1" -> newAmount += 1;
            case "+10" -> newAmount += 10;
            case "+100" -> newAmount += 100;
            case "+1000" -> newAmount += 1000;
            case "+10000" -> newAmount += 10000;
            case "-1" -> newAmount -= 1;
            case "-10" -> newAmount -= 10;
            case "-100" -> newAmount -= 100;
            case "-1000" -> newAmount -= 1000;
            case "-10000" -> newAmount -= 10000;
            case "購入確定" -> {
                if (newAmount > 0) {
                    double price = newAmount * claimBlockCost;
                    purchaseClaimBlocks(player, newAmount, price);
                    pendingPurchaseAmount.remove(player.getUniqueId());
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    openBlockPurchaseMenu(player, newAmount);
                }
                return;
            }
            case "戻る" -> {
                pendingPurchaseAmount.remove(player.getUniqueId());
                openMainMenu(player);
                return;
            }
            case "閉じる" -> {
                pendingPurchaseAmount.remove(player.getUniqueId());
                player.closeInventory();
                return;
            }
        }

        if (newAmount < 0) newAmount = 0;
        pendingPurchaseAmount.put(player.getUniqueId(), newAmount);
        openBlockPurchaseMenu(player, newAmount);
    }

    private void handleBlockSellClick(Player player, String itemName) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        int currentAmount = pendingSellAmount.getOrDefault(player.getUniqueId(), 0);
        int newAmount = currentAmount;

        PlayerData playerData = griefPrevention.dataStore.getPlayerData(player.getUniqueId());
        int remainingBlocks = playerData.getRemainingClaimBlocks();

        switch (itemName) {
            case "+1" -> newAmount += 1;
            case "+10" -> newAmount += 10;
            case "+100" -> newAmount += 100;
            case "+1000" -> newAmount += 1000;
            case "+10000" -> newAmount += 10000;
            case "-1" -> newAmount -= 1;
            case "-10" -> newAmount -= 10;
            case "-100" -> newAmount -= 100;
            case "-1000" -> newAmount -= 1000;
            case "-10000" -> newAmount -= 10000;
            case "売却確定" -> {
                if (newAmount > 0) {
                    sellClaimBlocks(player, newAmount);
                    pendingSellAmount.remove(player.getUniqueId());
                } else {
//                    player.sendMessage(Component.text("売却ブロック数は1以上にしてください！").color(NamedTextColor.RED));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    openBlockSellMenu(player,newAmount);
                }
//                pendingSellAmount.remove(player, newAmount);
//                player.closeInventory();
                return;
            }
            case "戻る" -> {
                pendingSellAmount.remove(player.getUniqueId());
                openMainMenu(player);
                return;
            }
            case "閉じる" -> {
                pendingSellAmount.remove(player.getUniqueId());
                player.closeInventory();
                return;
            }
        }

        if (newAmount < 0) newAmount = 0;
        pendingSellAmount.put(player.getUniqueId(), newAmount);
        openBlockSellMenu(player, newAmount);
    }

    private void handleClaimListClick(Player player, String itemName, ItemStack clicked, String titleText) {
        if (itemName.equals("戻る")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openMainMenu(player);
            return;
        }

        if (itemName.equals("閉じる")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            player.closeInventory();
            return;
        }

        int currentPage = Integer.parseInt(titleText.split("ページ ")[1].split("/")[0]);

        if (itemName.equals("◀ 前のページ")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openClaimListMenu(player, currentPage - 2);
            return;
        } else if (itemName.equals("次のページ ▶")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openClaimListMenu(player, currentPage);
            return;
        }

        if (clicked.getType() == Material.GRASS_BLOCK) {
            if (clicked.hasItemMeta() && clicked.getItemMeta().lore() != null) {
                List<Component> lore = clicked.getItemMeta().lore();
                String worldName = null;
                int x = 0, y = 0, z = 0;

                for (Component line : lore) {
                    String loreText = PlainTextComponentSerializer.plainText().serialize(line);

                    if (loreText.startsWith("ワールド: ")) {
                        worldName = loreText.replace("ワールド: ", "");
                    }
                    if (loreText.startsWith("座標: ")) {
                        try {
                            String coordText = loreText.replace("座標: ", "");
                            String[] parts = coordText.split(", ");
                            x = Integer.parseInt(parts[0].replace("X:", ""));
                            y = Integer.parseInt(parts[1].replace("Y:", ""));
                            z = Integer.parseInt(parts[2].replace("Z:", ""));
                        } catch (Exception e) {
                            player.sendMessage(Component.text("座標の解析に失敗しました!").color(NamedTextColor.RED));
                            return;
                        }
                    }
                }
                if (worldName != null) {
                    World targetWorld = Bukkit.getWorld(worldName);
                    if (targetWorld == null) {
                        player.sendMessage(Component.text("ワールドが見つかりません!").color(NamedTextColor.RED));
                        return;
                    }

                    try {
                        Location teleportLoc = new Location(targetWorld, x + 0.5, y, z + 0.5);
                        player.teleport(teleportLoc);
                        player.sendMessage(Component.text("土地の中心にテレポートしました!").color(NamedTextColor.GREEN));
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        player.closeInventory();
                    } catch (Exception e) {
                        player.sendMessage(Component.text("テレポートに失敗しました!").color(NamedTextColor.RED));
                        e.printStackTrace();
                    }
                } else {
                    player.sendMessage(Component.text("ワールド情報が見つかりません!").color(NamedTextColor.RED));
                }
            }
        }
    }
}