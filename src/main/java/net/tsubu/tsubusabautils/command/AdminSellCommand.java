package net.tsubu.tsubusabautils.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.tsubu.tsubusabautils.TsubusabaUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AdminSellCommand implements CommandExecutor {

    private final TsubusabaUtils plugin;

    public AdminSellCommand(TsubusabaUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("このコマンドはプレイヤーのみが使用できます。")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("tsubusabautils.adminsell.reload")) {
                player.sendMessage(Component.text("このコマンドを実行する権限がありません。")
                        .color(NamedTextColor.RED));
                return true;
            }

            plugin.getAdminSellManager().reloadConfig();
            player.sendMessage(Component.text("[運営ショップ] 設定を再読み込みしました。")
                    .color(NamedTextColor.GREEN));
            return true;
        }

        plugin.getAdminSellManager().openBuybackGUI(player);
        return true;
    }
}