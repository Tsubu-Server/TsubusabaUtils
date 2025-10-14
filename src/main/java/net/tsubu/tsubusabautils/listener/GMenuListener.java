package net.tsubu.tsubusabautils.listener;

import net.tsubu.tsubusabautils.TsubusabaUtils;
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

        event.setCancelled(true);
        plugin.getGriefPreventionMenuManager().openMainMenu(player);
    }
}