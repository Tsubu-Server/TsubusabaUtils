package net.tsubu.tsubusabautils.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.tsubu.tsubusabautils.TsubusabaUtils;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AdminSellCommand implements CommandExecutor {

    private final TsubusabaUtils plugin;

    public AdminSellCommand(TsubusabaUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("このコマンドはプレイヤー専用です。").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("tsubusabautils.adminsell.reload")) {
                player.sendMessage(Component.text("権限がありません。").color(NamedTextColor.RED));
                return true;
            }
            plugin.getAdminSellManager().reloadAll();
            player.sendMessage(Component.text("全AdminSellカテゴリを再読み込みしました。").color(NamedTextColor.GREEN));
            return true;
        }

        String category = (args.length >= 1) ? args[0].toLowerCase() : "default";
        plugin.getAdminSellManager().openCategory(player, category);
        return true;
    }
}
