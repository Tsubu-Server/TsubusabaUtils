package net.tsubu.tsubusabautils.command;

import net.tsubu.tsubusabautils.TsubusabaUtils;
import net.tsubu.tsubusabautils.manager.InvincibilityManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DeathCommand implements CommandExecutor {

    private final TsubusabaUtils plugin;
    private final InvincibilityManager invincibilityManager;

    public DeathCommand(TsubusabaUtils plugin, InvincibilityManager manager) {
        this.plugin = plugin;
        this.invincibilityManager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行可能です。");
            return true;
        }

        Location deathLoc = plugin.getDeathLocation(player);
        if (deathLoc == null) {
            player.sendMessage("§c死亡地点が記録されていません！");
            return true;
        }

        // テレポート
        player.teleport(deathLoc);
        player.sendMessage("§a死亡地点に戻りました！5秒間無敵状態です。");

        // 無敵5秒
        invincibilityManager.makeInvincible(player, 5);

        // 死亡地点を削除して1回限りにする
        plugin.removeDeathLocation(player);

        return true;
    }
}
