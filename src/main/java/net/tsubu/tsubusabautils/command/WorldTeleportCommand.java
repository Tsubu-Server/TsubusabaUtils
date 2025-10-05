package net.tsubu.tsubusabautils.command;

import net.tsubu.tsubusabautils.manager.WorldTeleportManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WorldTeleportCommand implements CommandExecutor {

    private final WorldTeleportManager manager;

    public WorldTeleportCommand(WorldTeleportManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ実行可能です。");
            return true;
        }

        String cmd = label.toLowerCase();
        Location targetLoc = null;
        String targetWorldName = null;
        World targetWorld = null;
        String worldPrefix = null;

        switch (cmd) {
            case "res":
                worldPrefix = "resource";
                break;
            case "main":
                worldPrefix = "world";
                break;
            case "trap":
                worldPrefix = "trap";
                break;
        }

        targetLoc = manager.getLastLocationByPrefix(player, worldPrefix);

        if (targetLoc != null) {
            targetWorldName = targetLoc.getWorld().getName();
            targetWorld = Bukkit.getWorld(targetLoc.getWorld().getName());
        } else {
            for (World world : Bukkit.getWorlds()) {
                if (world.getName().toLowerCase().startsWith(worldPrefix)) {
                    targetWorld = world;
                    targetLoc = world.getSpawnLocation();
                    targetWorldName = world.getName();
                    break;
                }
            }
        }

        if (targetLoc != null && targetWorld != null) {
            targetLoc.setWorld(targetWorld);
            player.teleport(targetLoc);
            player.sendMessage("§a" + targetWorldName + " に移動しました！");
        } else {
            player.sendMessage("§cワールドが存在しません。");
        }

        return true;
    }
}