package net.tsubu.tsubusabautils.manager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashMap;
import java.util.UUID;

public class InvincibilityManager implements Listener {

    private final HashMap<UUID, Long> invinciblePlayers = new HashMap<>();

    // プレイヤーを秒単位で無敵にする
    public void makeInvincible(Player player, int seconds) {
        invinciblePlayers.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    // ダメージキャンセル
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            Long endTime = invinciblePlayers.get(player.getUniqueId());
            if (endTime != null && System.currentTimeMillis() < endTime) {
                event.setCancelled(true);
            } else if (endTime != null) {
                // 無敵時間終了後は削除
                invinciblePlayers.remove(player.getUniqueId());
            }
        }
    }
}
