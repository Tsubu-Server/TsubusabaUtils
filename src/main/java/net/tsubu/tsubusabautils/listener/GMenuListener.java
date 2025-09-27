package net.tsubu.tsubusabautils.listener;

import net.tsubu.tsubusabautils.TsubusabaUtils;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class GMenuListener implements Listener {

    private final TsubusabaUtils plugin;

    public GMenuListener(TsubusabaUtils plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerLeftClick(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.LEFT_CLICK_AIR) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() != Material.PAPER) return;

        if (!canAccessGriefMenu(player)) return;

        event.setCancelled(true);
        plugin.getGriefPreventionMenuManager().openMainMenu(player);
    }

    /**
     * プレイヤーが現在の場所で土地メニューにアクセスできるかチェック
     */
    private boolean canAccessGriefMenu(Player player) {
        GriefPrevention gp = GriefPrevention.instance;
        if (gp == null) return false;
        Claim claim = gp.dataStore.getClaimAt(player.getLocation(), false, null);
        if (claim == null) return false;
        if (player.hasPermission("griefprevention.adminclaims")) {
            return true;
        }
        if (claim.getOwnerID() != null && claim.getOwnerID().equals(player.getUniqueId())) {
            return true;
        }
        String playerIdString = player.getUniqueId().toString();
        ClaimPermission permission = claim.getPermission(playerIdString);
        return permission != null;
    }
}