package net.tsubu.tsubusabautils.listener;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import net.tsubu.tsubusabautils.TsubusabaUtils;
import net.wesjd.anvilgui.AnvilGUI;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Collections;

public class SetHomeListener implements Listener {

    private final TsubusabaUtils plugin;
    private final Essentials essentials;

    public SetHomeListener(TsubusabaUtils plugin, Essentials essentials) {
        this.plugin = plugin;
        this.essentials = essentials;
    }

    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;

        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() != Material.GOLDEN_AXE) return;

        event.setCancelled(true);

        User user = essentials.getUser(player);
        if (user == null) return;

        int currentHomes = (user.getHomes() != null) ? user.getHomes().size() : 0;
        int maxHomes = getMaxHomes(player);

        if (currentHomes >= maxHomes) {
            player.sendMessage(Component.text("§cホームの上限に達しています！ (現在: " + currentHomes + " / 最大: " + maxHomes + ")"));
            return;
        }

        new AnvilGUI.Builder()
                .plugin(plugin)
                .title("ホーム名を入力")
                .text("home")
                .onClick((slot, stateSnapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                    String homeName = stateSnapshot.getText();
                    if (homeName == null || homeName.trim().isEmpty()) {
                        return Collections.singletonList(AnvilGUI.ResponseAction.replaceInputText("名前を入力してください"));
                    }
                    homeName = homeName.trim().toLowerCase();

                    Location loc = player.getLocation();
                    boolean isOverwrite = user.getHomes().contains(homeName);

                    try {
                        user.setHome(homeName, loc);

                        if (isOverwrite) {
                            player.sendMessage(Component.text("§eホーム「" + homeName + "」は既に存在していたため、§c新しい場所に上書きされました！"));
                        } else {
                            player.sendMessage(Component.text("§aホーム「" + homeName + "」を設定しました！"));
                        }

                        player.playSound(loc, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    } catch (Exception e) {
                        player.sendMessage(Component.text("§cホーム設定中にエラーが発生しました！"));
                        e.printStackTrace();
                    }

                    return Collections.singletonList(AnvilGUI.ResponseAction.close());
                })
                .open(player);
    }

    private int getMaxHomes(Player player) {
        int maxHomes = plugin.getConfig().getInt("sethome-multiple.default", 5);

        if (player.hasPermission("essentials.sethome.multiple.vip")) {
            int vipMax = plugin.getConfig().getInt("sethome-multiple.vip", 10);
            maxHomes = Math.max(maxHomes, vipMax);
        }

        if (player.hasPermission("essentials.sethome.multiple.staff")) {
            int staffMax = plugin.getConfig().getInt("sethome-multiple.staff", 18);
            maxHomes = Math.max(maxHomes, staffMax);
        }

        return maxHomes;
    }
}
