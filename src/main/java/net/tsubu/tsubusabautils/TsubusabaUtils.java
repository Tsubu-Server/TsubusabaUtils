package net.tsubu.tsubusabautils;

import net.milkbowl.vault.economy.Economy;
import net.tsubu.tsubusabautils.listener.JobJoinListener;
import net.tsubu.tsubusabautils.listener.JobLevelListener;
import net.tsubu.tsubusabautils.manager.AmountGUIManager;
import net.tsubu.tsubusabautils.manager.ChatManager;
import net.tsubu.tsubusabautils.manager.GUIManager;
import net.tsubu.tsubusabautils.manager.InvincibilityManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

public class TsubusabaUtils extends JavaPlugin implements Listener {

    // SendMoney系
    private ChatManager chatManager;
    private GUIManager guiManager;
    private AmountGUIManager amountGUIManager;
    private static Economy economy = null;

    // ReturnDeath系
    private final HashMap<UUID, Location> deathLocations = new HashMap<>();
    private InvincibilityManager invincibilityManager;

    @Override
    public void onEnable() {
        // Vaultセットアップ
        if (!setupEconomy()) {
            getLogger().warning("Vault economy not found. SendMoney features will be disabled.");
        }

        saveDefaultConfig();

        // Jobs連携リスナー
        getServer().getPluginManager().registerEvents(new JobLevelListener(this), this);
        getServer().getPluginManager().registerEvents(new JobJoinListener(this), this);

        // ReturnDeath用初期化
        invincibilityManager = new InvincibilityManager();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(invincibilityManager, this);

        // DeathCommand登録
        Objects.requireNonNull(this.getCommand("returndeath")).setExecutor(new DeathCommand(this, invincibilityManager));
        Objects.requireNonNull(this.getCommand("rd")).setExecutor(new DeathCommand(this, invincibilityManager));

        // SendMoney系初期化
        if (economy != null) {
            chatManager = new ChatManager(this);
            guiManager = new GUIManager(this);
            amountGUIManager = new AmountGUIManager(this);

            getServer().getPluginManager().registerEvents(chatManager, this);
            getServer().getPluginManager().registerEvents(guiManager, this);
            getServer().getPluginManager().registerEvents(amountGUIManager, this);

            this.getCommand("sendmoney").setExecutor(new SendMoneyCommand(this));
        }

        getLogger().info("TsubusabaUtils統合プラグインが有効になりました！");
    }

    @Override
    public void onDisable() {
        getLogger().info("TsubusabaUtils統合プラグインが無効になりました");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    // --- SendMoney系ゲッター ---
    public ChatManager getChatManager() { return chatManager; }
    public GUIManager getGuiManager() { return guiManager; }
    public AmountGUIManager getAmountGUIManager() { return amountGUIManager; }
    public static Economy getEconomy() { return economy; }

    // --- ReturnDeath系メソッド ---
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        deathLocations.put(player.getUniqueId(), player.getLocation());
    }

    public Location getDeathLocation(Player player) {
        return deathLocations.get(player.getUniqueId());
    }

    public void removeDeathLocation(Player player) {
        deathLocations.remove(player.getUniqueId());
    }
}
