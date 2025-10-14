package net.tsubu.tsubusabautils.manager;

import net.tsubu.tsubusabautils.TsubusabaUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BroomManager implements Listener {

    private final TsubusabaUtils plugin;
    private final Map<UUID, BroomSession> flyingPlayers = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 15000;
    private static final double FORWARD_SPEED = 0.8;
    private static final double ASCEND_SPEED = 0.5;
    private static final double DESCEND_SPEED = 0.4;

    public BroomManager(TsubusabaUtils plugin) {
        this.plugin = plugin;
    }

    public void mountBroom(Player player, ItemStack broom) {
        long now = System.currentTimeMillis();
        if (cooldowns.containsKey(player.getUniqueId()) && cooldowns.get(player.getUniqueId()) > now) {
            player.sendMessage(ChatColor.RED + "箒はクールダウン中です: " +
                    ((cooldowns.get(player.getUniqueId()) - now) / 1000) + "秒");
            return;
        }
        if (flyingPlayers.containsKey(player.getUniqueId())) return;

        // 半透明化はクールダウン中のみ
        BroomSession session = new BroomSession(player, broom);
        flyingPlayers.put(player.getUniqueId(), session);
        session.start();

        player.sendMessage(ChatColor.GREEN + "箒に乗って飛行開始！");
    }

    public void dismountBroom(Player player) {
        BroomSession session = flyingPlayers.remove(player.getUniqueId());
        if (session != null) {
            session.cancel();
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + COOLDOWN_MS);

            // クールダウン表示用にインベントリの箒を半透明化
            ItemStack broom = session.broomItem;
            if (broom != null) {
                ItemStack clone = broom.clone();
                ItemMeta meta = clone.getItemMeta();
                if (meta != null) meta.setCustomModelData(0); // 半透明用のモデルID
                clone.setItemMeta(meta);
                player.getInventory().remove(broom);
                player.getInventory().addItem(clone);
            }

            player.sendMessage(ChatColor.YELLOW + "箒を降りました。15秒クールダウン。");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        dismountBroom(event.getPlayer());
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        BroomSession session = flyingPlayers.get(player.getUniqueId());
        if (session == null) return;
        session.setDescending(event.isSneaking());
    }

    @EventHandler
    public void onPlayerJump(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        BroomSession session = flyingPlayers.get(player.getUniqueId());
        if (session == null) return;

        if (event.isFlying()) {
            session.setAscending(true);
            event.setCancelled(true); // 通常の飛行禁止
        } else {
            session.setAscending(false);
        }
    }

    private class BroomSession {
        private final Player player;
        private final ItemStack broomItem;
        private boolean ascending = false;
        private boolean descending = false;
        private BukkitRunnable task;

        public BroomSession(Player player, ItemStack broomItem) {
            this.player = player;
            this.broomItem = broomItem;
        }

        public void setAscending(boolean ascending) { this.ascending = ascending; }
        public void setDescending(boolean descending) { this.descending = descending; }

        public void start() {
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        cancel();
                        return;
                    }

                    org.bukkit.util.Vector dir = player.getLocation().getDirection().normalize().multiply(FORWARD_SPEED);

                    if (ascending) dir.setY(ASCEND_SPEED);
                    else if (descending) dir.setY(-DESCEND_SPEED);
                    else dir.setY(0);

                    player.setVelocity(dir);
                }
            };
            task.runTaskTimer(plugin, 0L, 1L);
        }

        public void cancel() {
            if (task != null) task.cancel();
        }
    }
}
