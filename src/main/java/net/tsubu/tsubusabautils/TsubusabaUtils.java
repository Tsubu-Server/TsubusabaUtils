package net.tsubu.tsubusabautils;

import net.milkbowl.vault.economy.Economy;
import net.tsubu.tsubusabautils.command.*;
import net.tsubu.tsubusabautils.listener.*;
import net.tsubu.tsubusabautils.manager.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import com.earth2me.essentials.Essentials;
import net.luckperms.api.LuckPerms;
import me.ryanhamshire.GriefPrevention.GriefPrevention;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

public class TsubusabaUtils extends JavaPlugin implements Listener {

    private static Economy economy = null;
    private HashMap<UUID, Location> deathLocations = new HashMap<>();

    private HomeManager homeManager;
    private HomeGUIManager homeGUIManager;
    private GUIManager guiManager;
    private AmountGUIManager amountGUIManager;
    private ChatManager chatManager;
    private InvincibilityManager invincibilityManager;
    private GriefPreventionMenuManager griefPreventionMenuManager;
    private AdminSellManager adminSellManager;
    private static TsubusabaUtils instance;
    private SidebarManager sidebarManager;
    private RecipeGUIManager recipeGUIManager;
    private GMenuListener gMenuListener;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault plugin not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Essentials essentials = (Essentials) getServer().getPluginManager().getPlugin("Essentials");
        if (essentials == null) {
            getLogger().severe("EssentialsX plugin not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        LuckPerms luckPerms = getServer().getServicesManager().load(LuckPerms.class);
        if (luckPerms == null) {
            getLogger().severe("LuckPerms plugin not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        GriefPrevention griefPrevention = (GriefPrevention) getServer().getPluginManager().getPlugin("GriefPrevention");
        if (griefPrevention == null) {
            getLogger().severe("GriefPrevention plugin not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        reloadConfig();

        instance = this;
        sidebarManager = new SidebarManager(this);
        this.homeManager = new HomeManager(essentials, this, luckPerms);
        this.homeGUIManager = new HomeGUIManager(this, homeManager);
        this.guiManager = new GUIManager(this);
        this.amountGUIManager = new AmountGUIManager(this);
        this.chatManager = new ChatManager(this);
        this.invincibilityManager = new InvincibilityManager();
        this.griefPreventionMenuManager = new GriefPreventionMenuManager(this, griefPrevention, economy);
        this.adminSellManager = new AdminSellManager(this, economy);
        this.recipeGUIManager = new RecipeGUIManager(this);
        this.gMenuListener = new GMenuListener(this);

        Objects.requireNonNull(this.getCommand("sendmoney")).setExecutor(new SendMoneyCommand(this));
        Objects.requireNonNull(this.getCommand("thome")).setExecutor(new ThomeCommand(this));
        Objects.requireNonNull(this.getCommand("returndeath")).setExecutor(new DeathCommand(this, invincibilityManager));
        Objects.requireNonNull(this.getCommand("gmenu")).setExecutor(new GMenuCommand(this));
        Objects.requireNonNull(this.getCommand("adminsell")).setExecutor(new AdminSellCommand(this));
        Objects.requireNonNull(this.getCommand("rec")).setExecutor(new RecipeCommand(recipeGUIManager));

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(amountGUIManager, this);
        getServer().getPluginManager().registerEvents(chatManager, this);
        getServer().getPluginManager().registerEvents(invincibilityManager, this);
        getServer().getPluginManager().registerEvents(griefPreventionMenuManager, this);
        getServer().getPluginManager().registerEvents(adminSellManager, this);
        getServer().getPluginManager().registerEvents(recipeGUIManager, this);
        getServer().getPluginManager().registerEvents(gMenuListener, this);
        getServer().getPluginManager().registerEvents(new MainMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new JobJoinListener(this, sidebarManager), this);
        getServer().getPluginManager().registerEvents(new JobLevelListener(this, sidebarManager), this);
        getServer().getPluginManager().registerEvents(new JobLeaveListener(this, sidebarManager), this);
        getServer().getPluginManager().registerEvents(new SetHomeListener(this, essentials), this);

        getLogger().info("TsubusabaUtilsが有効になりました。");

        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent event) {
                sidebarManager.updateJobs(event.getPlayer());
                sidebarManager.updateBalance(event.getPlayer());
            }
        }, this);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                sidebarManager.updateBalance(player);
            }
        }, 0L, 20L);
    }

    @Override
    public void onDisable() {
        getLogger().info("TsubusabaUtilsを無効化しました。");
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

    public static Economy getEconomy() {
        return economy;
    }

    public HomeManager getHomeManager() {
        return homeManager;
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    public HomeGUIManager getHomeGUIManager() {
        return homeGUIManager;
    }

    public AmountGUIManager getAmountGUIManager() {
        return amountGUIManager;
    }

    public ChatManager getChatManager() {
        return chatManager;
    }

    public InvincibilityManager getInvincibilityManager() {
        return invincibilityManager;
    }

    public GriefPreventionMenuManager getGriefPreventionMenuManager() {
        return griefPreventionMenuManager;
    }

    public AdminSellManager getAdminSellManager() {
        return adminSellManager;
    }

    public static TsubusabaUtils getInstance() {
        return instance;
    }

    public SidebarManager getSidebarManager() {
        return sidebarManager;
    }

    public GMenuListener getGMenuListener() {
        return gMenuListener;
    }
}