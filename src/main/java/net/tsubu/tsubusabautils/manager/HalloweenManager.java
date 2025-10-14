package net.tsubu.tsubusabautils.manager;

import net.kyori.adventure.title.Title;
import net.tsubu.tsubusabautils.TsubusabaUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HalloweenManager implements Listener {

    private final TsubusabaUtils plugin;
    private final Map<UUID, TrickSession> activeTricks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> attackerCooldowns = new ConcurrentHashMap<>();

    private final List<TrickItem> trickItems = new ArrayList<>();
    private final List<CandyItem> candyItems = new ArrayList<>();

    private static final int PUMPKIN_DURATION_TICKS = 100; // 5秒
    private static final int BAT_DURATION_TICKS = 200;

    private final DatabaseManager databaseManager;
    private Connection connection;
    private boolean databaseEnabled;
    private int trickDelayTicks;
    private long attackerCooldownMs;
    private int maxTricksPerDay;

    public HalloweenManager(TsubusabaUtils plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;

        loadConfigSettings();
        loadConfigItems();

        if (databaseManager != null && databaseManager.isEnabled()) {
            this.databaseEnabled = true;
            this.connection = databaseManager.getConnection();
            createTablesIfNeeded();
        } else {
            this.databaseEnabled = false;
        }
    }

    private void loadConfigSettings() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("halloween");
        if (section == null) return;

        trickDelayTicks = section.getInt("trick_delay_seconds", 10) * 20;
        attackerCooldownMs = section.getInt("attacker_cooldown_seconds", 5) * 1000L;
        maxTricksPerDay = section.getInt("max_tricks_per_day", 1);
    }

    private void loadConfigItems() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("halloween");
        if (section == null) {
            plugin.getLogger().warning("[Halloween] 設定ファイルにhalloweenセクションが見つかりません");
            return;
        }

        List<Map<?, ?>> tricks = section.getMapList("trick_items");
        for (Map<?, ?> map : tricks) {
            try {
                String name = ChatColor.translateAlternateColorCodes('&',
                        Objects.requireNonNull(map.get("name"), "name is null").toString());
                Material mat = Material.matchMaterial(
                        Objects.requireNonNull(map.get("material"), "material is null").toString());

                if (mat == null) {
                    plugin.getLogger().warning("[Halloween] 無効なマテリアル: " + map.get("material"));
                    continue;
                }

                int model = ((Number) map.get("custom_model_data")).intValue();

                Map<?, ?> recipeSection = (Map<?, ?>) map.get("recipe");
                if (recipeSection == null) {
                    plugin.getLogger().warning("[Halloween] レシピが見つかりません: " + name);
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, String> recipeMap = (Map<String, String>) recipeSection.get("ingredients");
                @SuppressWarnings("unchecked")
                List<String> shape = (List<String>) recipeSection.get("shape");

                trickItems.add(new TrickItem(name, mat, model, shape, recipeMap));
                plugin.getLogger().info("[Halloween] トリックアイテムを読み込みました: " + name);
            } catch (Exception e) {
                plugin.getLogger().warning("[Halloween] 無効なトリック定義をスキップ: " + map + " - " + e.getMessage());
            }
        }

        List<Map<?, ?>> candies = section.getMapList("candies");
        for (Map<?, ?> map : candies) {
            try {
                String name = ChatColor.translateAlternateColorCodes('&',
                        Objects.requireNonNull(map.get("name"), "name is null").toString());
                Material mat = Material.matchMaterial(
                        Objects.requireNonNull(map.get("material"), "material is null").toString());

                if (mat == null) {
                    plugin.getLogger().warning("[Halloween] 無効なマテリアル: " + map.get("material"));
                    continue;
                }

                int model = ((Number) map.get("custom_model_data")).intValue();

                Map<?, ?> recipeSection = (Map<?, ?>) map.get("recipe");
                if (recipeSection == null) {
                    plugin.getLogger().warning("[Halloween] レシピが見つかりません: " + name);
                    continue;
                }

                @SuppressWarnings("unchecked")
                List<String> ingredients = (List<String>) recipeSection.get("ingredients");

                candyItems.add(new CandyItem(name, mat, model, ingredients));
                plugin.getLogger().info("[Halloween] キャンディを読み込みました: " + name);
            } catch (Exception e) {
                plugin.getLogger().warning("[Halloween] 無効なキャンディ定義をスキップ: " + map + " - " + e.getMessage());
            }
        }
    }

    private void createTablesIfNeeded() {
        if (!databaseEnabled) return;
        String sql = """
        CREATE TABLE IF NOT EXISTS player_trick_history (
            attacker_uuid CHAR(36) NOT NULL,
            victim_uuid CHAR(36) NOT NULL,
            date DATE NOT NULL,
            PRIMARY KEY(attacker_uuid, victim_uuid, date)
        );
    """;
        databaseManager.createTable(sql);
    }

    private boolean canTrickToday(Player attacker, Player victim) {
        if (!databaseEnabled) return true;
        String sql = "SELECT * FROM player_trick_history WHERE attacker_uuid=? AND victim_uuid=? AND date=?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, attacker.getUniqueId().toString());
            stmt.setString(2, victim.getUniqueId().toString());
            stmt.setDate(3, new java.sql.Date(System.currentTimeMillis()));
            return !stmt.executeQuery().next();
        } catch (SQLException e) {
            plugin.getLogger().warning("DBチェック失敗: " + e.getMessage());
            return true;
        }
    }

    private void recordTrickToday(Player attacker, Player victim) {
        if (!databaseEnabled) return;
        String sql = "REPLACE INTO player_trick_history (attacker_uuid,victim_uuid,date) VALUES (?,?,?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, attacker.getUniqueId().toString());
            stmt.setString(2, victim.getUniqueId().toString());
            stmt.setDate(3, new java.sql.Date(System.currentTimeMillis()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB記録失敗: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!plugin.getConfig().getBoolean("halloween.enabled", true)) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        Player clicker = event.getPlayer();
        if (clicker.equals(target)) return;

        ItemStack item = clicker.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR || !item.hasItemMeta()) return;

        TrickSession session = activeTricks.get(target.getUniqueId());
        if (session != null && session.getAttacker().equals(clicker) && session.isCanTrick()) {
            tryTriggerTrick(clicker, target);
            event.setCancelled(true);
            return;
        }

        for (TrickItem trick : trickItems) {
            if (trick.matches(item)) {
                startTrickOrTreat(clicker, target);
                event.setCancelled(true);
                return;
            }
        }

        for (CandyItem candy : candyItems) {
            if (candy.matches(item)) {
                handleTreat(clicker);
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        TrickSession session = activeTricks.remove(uuid);
        if (session != null) {
            session.cancel();
            Player attacker = session.getAttacker();
            if (attacker != null && attacker.isOnline()) {
                showTitle(attacker, "キャンセル",
                        player.getName() + " がログアウトしました",
                        255, 165, 0, 255, 255, 255);
            }
        }

        activeTricks.values().removeIf(s -> {
            if (s.getAttacker().equals(player)) {
                s.cancel();
                Player victim = s.getVictim();
                if (victim != null && victim.isOnline()) {
                    showTitle(victim, "キャンセル",
                            player.getName() + " がログアウトしました",
                            255, 165, 0, 255, 255, 255);
                }
                return true;
            }
            return false;
        });

        attackerCooldowns.remove(uuid);
    }

    private void startTrickOrTreat(Player attacker, Player victim) {
        if (!canTrickToday(attacker, victim)) {
            showTitle(attacker, "失敗！", victim.getName() + " には今日すでにいたずら済み", 255,0,0,255,255,255);
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (activeTricks.containsKey(victim.getUniqueId())) {
            showTitle(attacker, "待って！", "そのプレイヤーには既に仕掛けています",
                    255, 0, 0, 255, 255, 255);
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        long currentTime = System.currentTimeMillis();
        Long lastAttack = attackerCooldowns.get(attacker.getUniqueId());
        if (lastAttack != null && (currentTime - lastAttack) < attackerCooldownMs) {
            long remaining = (attackerCooldownMs - (currentTime - lastAttack)) / 1000;
            showTitle(attacker, "クールダウン中",
                    "あと " + remaining + " 秒待ってください",
                    255, 165, 0, 255, 255, 255);
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        TrickSession session = new TrickSession(attacker, victim, plugin);
        activeTricks.put(victim.getUniqueId(), session);
        attackerCooldowns.put(attacker.getUniqueId(), currentTime);

        showTitle(attacker, "トリックオアトリート！", victim.getName() + " に仕掛けた！",
                255, 165, 0, 0, 255, 255);
        showTitle(victim, "トリックオアトリート！", attacker.getName() + " が仕掛けた！",
                255, 165, 0, 0, 255, 255);

        attacker.playSound(attacker.getLocation(), Sound.ENTITY_WITCH_AMBIENT, 1f, 1f);
        victim.playSound(victim.getLocation(), Sound.ENTITY_WITCH_AMBIENT, 1f, 1f);

        BukkitTask task = new BukkitRunnable() {
            int secondsLeft = trickDelayTicks / 20;

            @Override
            public void run() {
                if (!attacker.isOnline() || !victim.isOnline() || session.isTreated()) {
                    cancel();
                    return;
                }

                if (secondsLeft > 0) {
                    String msg = ChatColor.YELLOW + "トリックまであと " + secondsLeft + " 秒";
                    attacker.sendMessage(msg);
                    victim.sendMessage(msg);
                    attacker.playSound(attacker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                    victim.playSound(victim.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                    secondsLeft--;
                } else {
                    session.setCanTrick(true);
                    showTitle(attacker, "トリックチャンス！", victim.getName() + " にいたずらをしちゃおう！",
                            255, 69, 0, 255, 255, 255);
                    showTitle(victim, "トリート失敗...", attacker.getName() + " のいたずらの時間だ",
                            255, 0, 0, 255, 255, 255);
                    attacker.playSound(attacker.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                    victim.playSound(victim.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 20);

        session.setTask(task);
    }

    private void tryTriggerTrick(Player clicker, Player target) {
        TrickSession session = activeTricks.get(target.getUniqueId());
        if (session == null) {
            return;
        }

        if (!session.getAttacker().equals(clicker)) {
            return;
        }

        if (!session.isCanTrick()) {
            return;
        }
        triggerRandomTrick(session);

        session.setCanTrick(false);
    }

    private void handleTreat(Player receiver) {
        TrickSession session = activeTricks.get(receiver.getUniqueId());
        if (session == null) return;

        Player attacker = session.getAttacker();
        ItemStack held = receiver.getInventory().getItemInMainHand();

        CandyItem candy = candyItems.stream().filter(c -> c.matches(held)).findFirst().orElse(null);
        if (candy == null || held.getAmount() < 1) return;
        held.setAmount(held.getAmount() - 1);
        ItemStack give = new ItemStack(held.getType(), 1);
        ItemMeta meta = held.getItemMeta();
        if (meta != null) give.setItemMeta(meta.clone());
        HashMap<Integer, ItemStack> leftover = attacker.getInventory().addItem(give);
        if (!leftover.isEmpty()) {
            attacker.getWorld().dropItem(attacker.getLocation(), give);
        }
        recordTrickToday(attacker, receiver);
        session.setTreated(true);
        session.cancel();
        activeTricks.remove(receiver.getUniqueId());
        showTitle(receiver, "トリート成功！", "キャンディを渡したぞ！",
                0, 255, 0, 255, 255, 255);
        showTitle(attacker, "お菓子を受け取った！", receiver.getName() + " からキャンディを受け取った！",
                255, 0, 0, 255, 255, 255);
        receiver.playSound(receiver.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f); // オーブ効果音
        receiver.spawnParticle(Particle.HEART, receiver.getLocation().add(0, 2, 0), 20, 0.5, 0.5, 0.5);
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }


    private void triggerRandomTrick(TrickSession session) {
        if (!session.isCanTrick()) return;

        Player victim = session.getVictim();
        Player attacker = session.getAttacker();

        if (!victim.isOnline() || !attacker.isOnline()) {
            activeTricks.remove(victim.getUniqueId());
            return;
        }

        activeTricks.remove(victim.getUniqueId());
        recordTrickToday(attacker, victim);

        int choice = new Random().nextInt(3);
        switch (choice) {
            case 0 -> applyPumpkinHead(attacker, victim);
            case 1 -> applyBlindness(victim);
            case 2 -> applyExplosionAndBats(victim);
        }

        showTitle(attacker, "トリック発動！", victim.getName() + " にいたずらした！",
                255, 69, 0, 255, 255, 255);
        showTitle(victim, "いたずらされた！", "トリックオアトリート",
                255, 0, 0, 255, 255, 255);
    }

    private void applyPumpkinHead(Player attacker, Player victim) {
        ItemStack original = victim.getInventory().getHelmet();
        ItemStack pumpkin = new ItemStack(Material.CARVED_PUMPKIN);
        ItemMeta meta = pumpkin.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
            pumpkin.setItemMeta(meta);
        }
        victim.getInventory().setHelmet(pumpkin);
        victim.getWorld().spawnParticle(Particle.GUST_EMITTER_LARGE, victim.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.05);
        victim.playSound(victim.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 1f);
        attacker.playSound(victim.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 1f);
        victim.playSound(victim.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 1f, 1f);
        attacker.playSound(victim.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 1f, 1f);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (victim.isOnline() && victim.getInventory().getHelmet() != null
                        && victim.getInventory().getHelmet().getType() == Material.CARVED_PUMPKIN) {
                    victim.getInventory().setHelmet(original);
                    victim.playSound(victim.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1f);
                    attacker.playSound(victim.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1f);
                }
            }
        }.runTaskLater(plugin, PUMPKIN_DURATION_TICKS);
    }

    private void applyBlindness(Player victim) {
        victim.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.BLINDNESS, 100, 1));
        victim.playSound(victim.getLocation(), Sound.ENTITY_BAT_AMBIENT, 1f, 0.5f);
    }

    private void applyExplosionAndBats(Player victim) {
        Location loc = victim.getLocation().add(0, 1, 0);
        victim.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
        victim.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);

        List<Bat> bats = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Location spawnLoc = loc.clone().add(
                    (Math.random() - 0.5) * 2,
                    Math.random() * 2,
                    (Math.random() - 0.5) * 2
            );
            Bat bat = victim.getWorld().spawn(spawnLoc, Bat.class, b -> {
                b.setCustomName("TrickBat");
                b.setSilent(true);
                b.setAI(true);
            });
            bats.add(bat);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Bat bat : bats) {
                    if (bat != null && !bat.isDead()) {
                        bat.remove();
                    }
                }
            }
        }.runTaskLater(plugin, BAT_DURATION_TICKS);
    }

    private void showTitle(Player player, String title, String subtitle,
                           int r1, int g1, int b1, int r2, int g2, int b2) {
        Component t = Component.text(title).color(TextColor.color(r1, g1, b1));
        Component st = Component.text(subtitle).color(TextColor.color(r2, g2, b2));
        Title.Times times = Title.Times.of(
                Duration.ofMillis(500),
                Duration.ofMillis(3500),
                Duration.ofMillis(500)
        );

        Title advTitle = Title.title(t, st, times);
        player.showTitle(advTitle);
    }

    public void registerRecipes() {
        int trickCount = 0;
        int candyCount = 0;

        for (TrickItem trick : trickItems) {
            try {
                trick.register(plugin);
                trickCount++;
            } catch (Exception e) {
                plugin.getLogger().warning("[Halloween] レシピ登録失敗 (Trick): " + trick.name + " - " + e.getMessage());
            }
        }

        for (CandyItem candy : candyItems) {
            try {
                candy.register(plugin);
                candyCount++;
            } catch (Exception e) {
                plugin.getLogger().warning("[Halloween] レシピ登録失敗 (Candy): " + candy.name + " - " + e.getMessage());
            }
        }

        plugin.getLogger().info("[Halloween] レシピを登録しました - Trick: " + trickCount + ", Candy: " + candyCount);
    }

    public void cleanup() {
        for (TrickSession session : activeTricks.values()) {
            session.cancel();
        }
        activeTricks.clear();
        attackerCooldowns.clear();
    }

    private static class TrickSession {
        private final Player attacker;
        private final Player victim;
        private boolean treated = false;
        private boolean canTrick = false;
        private BukkitTask task;

        public TrickSession(Player attacker, Player victim, TsubusabaUtils plugin) {
            this.attacker = attacker;
            this.victim = victim;
        }

        public Player getAttacker() { return attacker; }
        public Player getVictim() { return victim; }
        public boolean isTreated() { return treated; }
        public void setTreated(boolean treated) { this.treated = treated; }
        public boolean isCanTrick() { return canTrick; }
        public void setCanTrick(boolean canTrick) { this.canTrick = canTrick; }

        public void setTask(BukkitTask task) {
            this.task = task;
        }

        public void cancel() {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
    }

    private static class TrickItem {
        public final String name;
        public final Material material;
        public final int modelData;
        public final List<String> shape;
        public final Map<String, String> ingredients;

        public TrickItem(String name, Material material, int modelData,
                         List<String> shape, Map<String, String> ingredients) {
            this.name = name;
            this.material = material;
            this.modelData = modelData;
            this.shape = shape;
            this.ingredients = ingredients;
        }

        public boolean matches(ItemStack item) {
            if (item == null || !item.hasItemMeta()) return false;
            ItemMeta meta = item.getItemMeta();
            return item.getType() == material
                    && meta.hasCustomModelData()
                    && meta.getCustomModelData() == modelData;
        }

        public void register(TsubusabaUtils plugin) {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;

            meta.setCustomModelData(modelData);
            meta.setDisplayName(name);
            item.setItemMeta(meta);

            NamespacedKey key = new NamespacedKey(plugin, "trick_" + modelData);
            ShapedRecipe recipe = new ShapedRecipe(key, item);

            if (shape != null && !shape.isEmpty()) {
                recipe.shape(shape.toArray(new String[0]));
            }

            for (Map.Entry<String, String> e : ingredients.entrySet()) {
                Material mat = Material.matchMaterial(e.getValue());
                if (mat != null && !e.getKey().isEmpty()) {
                    recipe.setIngredient(e.getKey().charAt(0), mat);
                }
            }

            plugin.getServer().addRecipe(recipe);
        }
    }

    private static class CandyItem {
        public final String name;
        public final Material material;
        public final int modelData;
        public final List<String> ingredients;

        public CandyItem(String name, Material material, int modelData, List<String> ingredients) {
            this.name = name;
            this.material = material;
            this.modelData = modelData;
            this.ingredients = ingredients;
        }

        public boolean matches(ItemStack item) {
            if (item == null || !item.hasItemMeta()) return false;
            ItemMeta meta = item.getItemMeta();
            return item.getType() == material
                    && meta.hasCustomModelData()
                    && meta.getCustomModelData() == modelData
                    && meta.hasDisplayName()
                    && meta.getDisplayName().equals(name);
        }

        public void register(TsubusabaUtils plugin) {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;

            meta.setCustomModelData(modelData);
            meta.setDisplayName(name);
            item.setItemMeta(meta);

            NamespacedKey key = new NamespacedKey(plugin, "candy_" + modelData);
            ShapelessRecipe recipe = new ShapelessRecipe(key, item);

            for (String ing : ingredients) {
                Material mat = Material.matchMaterial(ing);
                if (mat != null) {
                    recipe.addIngredient(mat);
                }
            }

            plugin.getServer().addRecipe(recipe);
        }
    }
}