package net.tsubu.tsubusabautils.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.tsubu.tsubusabautils.TsubusabaUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class WikiCommand implements CommandExecutor {

    private final TsubusabaUtils plugin;
    private final String wikiUrl = "https://tsubu-server.github.io/";

    public WikiCommand(TsubusabaUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはゲーム内プレイヤーのみ実行できます。");
            return true;
        }

        Component message = Component.text("クリックでガイドを開く: ", NamedTextColor.YELLOW)
                .append(Component.text(wikiUrl, NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.openUrl(wikiUrl)));

        player.sendMessage(message);
        return true;
    }
}
