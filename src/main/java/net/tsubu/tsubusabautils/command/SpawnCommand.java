package net.tsubu.tsubusabautils.command;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SpawnCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ実行可能です。");
            return true;
        }

        World currentWorld = player.getWorld();
        Location spawnLocation = currentWorld.getSpawnLocation();

        player.teleport(spawnLocation);
        player.sendMessage("§aスポーン地点に移動しました！");

        return true;
    }
}