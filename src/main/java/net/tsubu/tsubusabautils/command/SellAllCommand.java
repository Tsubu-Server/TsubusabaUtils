package net.tsubu.tsubusabautils.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.EconomyResponse;
import net.tsubu.tsubusabautils.TsubusabaUtils;
import net.tsubu.tsubusabautils.manager.AdminSellManager;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SellAllCommand implements CommandExecutor {
    private final TsubusabaUtils plugin;
    private final AdminSellManager adminSellManager;
    private final DecimalFormat df = new DecimalFormat("#,##0.##");

    public SellAllCommand(TsubusabaUtils plugin) {
        this.plugin = plugin;
        this.adminSellManager = plugin.getAdminSellManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ使用できます。");
            return true;
        }

        Map<Integer, ItemStack> sellMap = new HashMap<>();
        double total = 0.0;

        ItemStack[] contents = player.getInventory().getContents();
        Map<Material, Double> buyMap = adminSellManager.getBuybackItems();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir()) continue;
            if (item.getItemMeta() instanceof BlockStateMeta bsm && item.getType().name().endsWith("SHULKER_BOX")) {
                ShulkerBox box = (ShulkerBox) bsm.getBlockState();
                Inventory boxInv = box.getInventory();
                List<ItemStack> toRemove = new ArrayList<>();
                double boxTotal = 0.0;

                for (ItemStack inside : boxInv.getContents()) {
                    if (inside == null) continue;
                    if (buyMap.containsKey(inside.getType())) {
                        double price = buyMap.get(inside.getType());
                        boxTotal += price * inside.getAmount();
                        toRemove.add(inside);
                    }
                }

                if (boxTotal > 0) {
                    for (ItemStack removeItem : toRemove) {
                        boxInv.remove(removeItem);
                    }
                    bsm.setBlockState(box);
                    item.setItemMeta(bsm);
                    player.getInventory().setItem(i, item);
                    total += boxTotal;
                }

            } else {
                if (!buyMap.containsKey(item.getType())) continue;
                double price = buyMap.get(item.getType());
                total += price * item.getAmount();
                sellMap.put(i, item);
            }
        }

        if (total <= 0) {
            player.sendMessage(Component.text("売却可能なアイテムがありません。", NamedTextColor.RED));
            return true;
        }

        for (int slot : sellMap.keySet()) {
            player.getInventory().setItem(slot, null);
        }

        EconomyResponse resp = TsubusabaUtils.getEconomy().depositPlayer(player, total);
        if (resp.transactionSuccess()) {
            player.sendMessage(Component.text("§a" + df.format(total) + "D で売却しました!"));
        } else {
            player.sendMessage(Component.text("支払いエラー: " + resp.errorMessage, NamedTextColor.RED));
        }
        player.updateInventory();
        return true;
    }
}