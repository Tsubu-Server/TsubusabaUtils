package net.tsubu.tsubusabautils.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.tsubu.tsubusabautils.TsubusabaUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class GMenuCommand implements CommandExecutor {

    private final TsubusabaUtils plugin;

    public GMenuCommand(TsubusabaUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("このコマンドはプレイヤーのみが使用できます！").color(NamedTextColor.RED));
            return true;
        }

        plugin.getGriefPreventionMenuManager().openMainMenu(player);
//        player.sendMessage(Component.text("土地メニューを開きました！").color(NamedTextColor.GREEN));

        return true;
    }
}