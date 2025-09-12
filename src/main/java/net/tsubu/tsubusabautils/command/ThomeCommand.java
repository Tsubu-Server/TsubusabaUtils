package net.tsubu.tsubusabautils.command;

import net.tsubu.tsubusabautils.TsubusabaUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ThomeCommand implements CommandExecutor {

    private final TsubusabaUtils plugin;

    public ThomeCommand(TsubusabaUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("コンソールからは使えません");
            return true;
        }

        if (args.length == 0) {
            plugin.getHomeGUIManager().openHomeGUI(player);
            return true;
        }

        String homeName = args[0];
        if (plugin.getHomeGUIManager().teleportToHome(player, homeName)) {
            return true;
        } else {
            player.sendMessage("§cホームが見つかりません: " + homeName);
            return false;
        }
    }
}