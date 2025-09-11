package net.tsubu.tsubusabautils;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SendMoneyCommand implements CommandExecutor {

    private final TsubusabaUtils plugin;

    public SendMoneyCommand(TsubusabaUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("sendmoney")) {
            Player player = (Player) sender;

            if (plugin.getGuiManager() == null) {
                player.sendMessage("SendMoney feature is not available (Vault not found).");
                return true;
            }

            plugin.getGuiManager().openGUI(player, 0);
            return true;
        }

        return false;
    }
}