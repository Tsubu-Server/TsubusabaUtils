package net.tsubu.tsubusabautils.manager;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.tsubu.tsubusabautils.TsubusabaUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChatManager implements Listener {

    private final TsubusabaUtils plugin;
    private final Map<UUID, Player> awaitingAmount = new HashMap<>();

    public ChatManager(TsubusabaUtils plugin) {
        this.plugin = plugin;
    }

    public void startPaymentProcess(Player sender, Player target) {
        awaitingAmount.put(sender.getUniqueId(), target);

        sender.playSound(sender.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text(target.getName() + " へ送金します")
                .color(NamedTextColor.GREEN));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("チャットに金額（数字）を入力してください（例：50, 100, 1000）")
                .color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("取引をキャンセルするには'cancel'と入力してください")
                .color(NamedTextColor.RED));
        sender.sendMessage(Component.empty());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (!awaitingAmount.containsKey(playerUUID)) return;

        event.setCancelled(true);
        Player target = awaitingAmount.remove(playerUUID);

        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text("取引がキャンセルされました： 対象プレイヤーがオフラインになりました").color(NamedTextColor.RED));
            return;
        }

        String message = event.getMessage().trim();
        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage(Component.text("取引中止").color(NamedTextColor.YELLOW));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(message);
            if (amount <= 0) {
                player.sendMessage(Component.text("正の数を入力してください").color(NamedTextColor.RED));
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("無効な金額です。有効な数字を入力するか、「キャンセル」してください").color(NamedTextColor.RED));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            Economy economy = TsubusabaUtils.getEconomy();
            if (economy == null) {
                player.sendMessage(Component.text("Economy plugin not found. Cannot process payment.").color(NamedTextColor.RED));
                return;
            }

            if (!economy.has(player, amount)) {
                player.sendMessage(Component.text("所持金が不足しています").color(NamedTextColor.RED));
                return;
            }

            EconomyResponse response = economy.withdrawPlayer(player, amount);
            if (response.transactionSuccess()) {
                EconomyResponse depositResponse = economy.depositPlayer(target, amount);
                if (depositResponse.transactionSuccess()) {
                    player.sendMessage(Component.text("送金に成功しました！ " + target.getName() + "に $" + formatAmount(amount) + " 送りました").color(NamedTextColor.GREEN));
                    target.sendMessage(Component.text(player.getName() + "から $" + formatAmount(amount) + " 受け取りました").color(NamedTextColor.GREEN));
                } else {
                    economy.depositPlayer(player, amount);
                    player.sendMessage(Component.text("支払いに失敗しました！お金は返却されます").color(NamedTextColor.RED));
                }
            } else {
                player.sendMessage(Component.text("支払いに失敗しました！もう一度お試しください").color(NamedTextColor.RED));
            }
        });
    }

    private String formatAmount(double amount) {
        if (amount == (long) amount) {
            return String.format("%d", (long) amount);
        } else {
            return String.format("%.2f", amount);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        awaitingAmount.remove(event.getPlayer().getUniqueId());
    }

    public boolean isAwaitingPayment(Player player) {
        return awaitingAmount.containsKey(player.getUniqueId());
    }

    public void cancelPayment(Player player) {
        awaitingAmount.remove(player.getUniqueId());
    }

    public void processPayment(Player sender, Player target, double amount) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Economy economy = TsubusabaUtils.getEconomy();
            if (economy == null) {
                sender.sendMessage(Component.text("Economy plugin not found. Cannot process payment.")
                        .color(NamedTextColor.RED));
                return;
            }

            if (!economy.has(sender, amount)) {
                sender.sendMessage(Component.text("所持金が不足しています").color(NamedTextColor.RED));
                return;
            }

            EconomyResponse response = economy.withdrawPlayer(sender, amount);
            if (response.transactionSuccess()) {
                EconomyResponse depositResponse = economy.depositPlayer(target, amount);
                if (depositResponse.transactionSuccess()) {
                    sender.sendMessage(Component.text("送金に成功しました！ " + target.getName() +
                            " に $" + formatAmount(amount) + " 送りました").color(NamedTextColor.GREEN));
                    target.sendMessage(Component.text(sender.getName() +
                            " から $" + formatAmount(amount) + " 受け取りました").color(NamedTextColor.GREEN));
                } else {
                    economy.depositPlayer(sender, amount); // 失敗したら返金
                    sender.sendMessage(Component.text("支払いに失敗しました！お金は返却されます")
                            .color(NamedTextColor.RED));
                }
            } else {
                sender.sendMessage(Component.text("支払いに失敗しました！もう一度お試しください")
                        .color(NamedTextColor.RED));
            }
        });
    }
}