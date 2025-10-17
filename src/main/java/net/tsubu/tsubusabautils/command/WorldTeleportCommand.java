package net.tsubu.tsubusabautils.command;

import net.tsubu.tsubusabautils.manager.WorldTeleportManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class WorldTeleportCommand implements CommandExecutor {

    private final WorldTeleportManager manager;

    public WorldTeleportCommand(WorldTeleportManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ実行可能です。");
            return true;
        }

        String cmd = label.toLowerCase();
        String targetWorldName;
        switch (cmd) {
            case "res" -> targetWorldName = "resource";
            case "main" -> targetWorldName = "world";
            default -> {
                player.sendMessage("§c不明なコマンドです。");
                return true;
            }
        }

        Location lastLoc = manager.getLastLocationByPrefix(player, targetWorldName);
        Location targetLoc;

        if (lastLoc != null && lastLoc.getWorld() != null && lastLoc.getWorld().getName().toLowerCase().startsWith(targetWorldName.toLowerCase())) {
            targetLoc = lastLoc.clone();
            targetLoc.setX(targetLoc.getBlockX() + 0.5);
            targetLoc.setZ(targetLoc.getBlockZ() + 0.5);
        } else {
            World targetWorld = Bukkit.getWorld(targetWorldName);
            if (targetWorld == null) {
                player.sendMessage("§cワールド '" + targetWorldName + "' が存在しません。");
                return true;
            }
            targetLoc = targetWorld.getSpawnLocation().clone();
            targetLoc.setX(targetLoc.getBlockX() + 0.5);
            targetLoc.setZ(targetLoc.getBlockZ() + 0.5);
            targetLoc.setY(targetWorld.getHighestBlockYAt(targetLoc) + 1);
        }

        player.sendMessage("§7転送準備中...");

        Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
            player.teleport(targetLoc); // 同期テレポート
            player.sendMessage("§a" + targetLoc.getWorld().getName() + " に移動しました！");
        });

        return true;
    }
}
