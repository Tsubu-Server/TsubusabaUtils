package net.tsubu.tsubusabautils.command;

import net.tsubu.tsubusabautils.manager.RecipeGUIManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RecipeCommand implements CommandExecutor {

    private final RecipeGUIManager recipeGUIManager;

    public RecipeCommand(RecipeGUIManager recipeGUIManager) {
        this.recipeGUIManager = recipeGUIManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤー専用です。");
            return true;
        }
        recipeGUIManager.openRecipePage(player, 1);
        return true;
    }
}
