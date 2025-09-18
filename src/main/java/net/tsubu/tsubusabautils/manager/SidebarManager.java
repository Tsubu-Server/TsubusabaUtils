package net.tsubu.tsubusabautils.manager;

import net.tsubu.tsubusabautils.TsubusabaUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.container.JobProgression;
import com.gamingmesh.jobs.container.JobsPlayer;

import java.util.HashMap;
import java.util.Map;

public class SidebarManager {

    private final TsubusabaUtils plugin;
    private final Map<Player, Scoreboard> playerBoards = new HashMap<>();

    public SidebarManager(TsubusabaUtils plugin) {
        this.plugin = plugin;
    }

    public void updateBalance(Player player) {
        Scoreboard board = getBoard(player);
        Objective obj = getObjective(board);

        if (plugin.getConfig().getBoolean("sidebar.show-money", true)) {
            double balanceValue = TsubusabaUtils.getEconomy().getBalance(player);
            balanceValue = Math.floor(balanceValue * 10) / 10.0;
            String balanceStr = balanceValue + "$";

            for (String entry : board.getEntries()) {
                if (entry.contains("所持金:")) {
                    board.resetScores(entry);
                }
            }
            String moneyLine = ChatColor.BOLD + "" + ChatColor.YELLOW + "所持金: " + ChatColor.GREEN + balanceStr;
            obj.getScore(moneyLine).setScore(10);
        }

        player.setScoreboard(board);
    }

    public void updateJobs(Player player) {
        Scoreboard board = getBoard(player);
        Objective obj = getObjective(board);

        if (!plugin.getConfig().getBoolean("sidebar.show-jobs", true)) return;
        for (String entry : board.getEntries()) {
            if (!entry.contains("所持金:")) {
                board.resetScores(entry);
            }
        }

        try {
            JobsPlayer jobsPlayer = Jobs.getPlayerManager().getJobsPlayer(player);
            if (jobsPlayer == null) return;

            int maxJobs = plugin.getConfig().getInt("sidebar.max-jobs-display", 3);
            int score = 9;
            obj.getScore(ChatColor.GOLD + "" + ChatColor.BOLD + "職業").setScore(score--);

            int count = 0;
            for (JobProgression progression : jobsPlayer.getJobProgression()) {
                if (progression != null) {
                    String displayName = progression.getJob().getDisplayName();
                    int level = progression.getLevel();
                    obj.getScore(displayName + " " + ChatColor.GOLD + "Lv" + level)
                            .setScore(score--);
                    count++;
                    if (count >= maxJobs) break;
                }
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Sidebar update error: " + e.getMessage());
        }

        player.setScoreboard(board);
    }

    public void removeJob(Player player, String jobDisplayName) {
        Scoreboard board = getBoard(player);
        Objective obj = getObjective(board);

        for (String entry : board.getEntries()) {
            if (entry.contains(jobDisplayName)) {
                board.resetScores(entry);
            }
        }

        player.setScoreboard(board);
    }

    private Scoreboard getBoard(Player player) {
        return playerBoards.computeIfAbsent(player, p -> Bukkit.getScoreboardManager().getNewScoreboard());
    }

    private Objective getObjective(Scoreboard board) {
        Objective obj = board.getObjective("sidebar");
        if (obj == null) {
            String rawTitle = plugin.getConfig().getString("sidebar.title", "§6§lステータス");
            String title = ChatColor.translateAlternateColorCodes('&', rawTitle);
            obj = board.registerNewObjective("sidebar", "dummy", title);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        return obj;
    }
}
