package net.tsubu.tsubusabautils.listener;

import net.tsubu.tsubusabautils.TsubusabaUtils;
import org.bukkit.advancement.Advancement;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.advancement.AdvancementProgress;

import com.gamingmesh.jobs.api.JobsJoinEvent;
import com.gamingmesh.jobs.container.JobsPlayer;
import com.gamingmesh.jobs.container.JobProgression;

public class JobJoinListener implements Listener {

    private final TsubusabaUtils plugin;

    public JobJoinListener(TsubusabaUtils plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJobJoin(JobsJoinEvent event) {
        Player player = event.getPlayer().getPlayer();
        if (player == null) return;
        String jobName = event.getJob().getName();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            processJobCountAdvancements(player);          // any-job-join.first, second, third
            processSpecificJobAdvancements(player, jobName); // specific-job-join.Miner など
        }, 1L);
    }

    /**
     * 職業数による進捗を処理
     */
    private void processJobCountAdvancements(Player player) {
        int jobCount = getPlayerJobCount(player);

        String advancementId = null;
        switch (jobCount) {
            case 1:
                advancementId = plugin.getConfig().getString("any-job-join.first");
                break;
            case 2:
                advancementId = plugin.getConfig().getString("any-job-join.second");
                break;
            case 3:
                advancementId = plugin.getConfig().getString("any-job-join.third");
                break;
            default:
                break;
        }

        if (advancementId != null && !advancementId.isEmpty()) {
            grantAdvancement(player, advancementId);
            player.sendMessage(jobCount + "個目の職業に就職！");
        }
    }

    /**
     * 特定職業による進捗を処理
     */
    private void processSpecificJobAdvancements(Player player, String jobName) {
        String advancementId = plugin.getConfig().getString("specific-job-join." + jobName);

        if (advancementId != null && !advancementId.isEmpty()) {
            grantAdvancement(player, advancementId);
        }
    }

    /**
     * プレイヤーの職業数を取得
     */
    private int getPlayerJobCount(Player player) {
        try {
            JobsPlayer jobsPlayer = com.gamingmesh.jobs.Jobs.getPlayerManager().getJobsPlayer(player);
            if (jobsPlayer == null) return 0;

            int count = 0;
            for (JobProgression progression : jobsPlayer.getJobProgression()) {
                if (progression != null) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting job count for " + player.getName() + ": " + e.getMessage());
            return 0;
        }
    }

    /**
     * プレイヤーに進捗を付与
     */
    private void grantAdvancement(Player player, String id) {
        if (id == null || id.isEmpty()) return;

        String[] parts = id.split(":", 2);
        if (parts.length != 2) {
            plugin.getLogger().warning("Invalid advancement ID format: " + id + " (expected format: namespace:key)");
            return;
        }

        try {
            NamespacedKey key = new NamespacedKey(parts[0], parts[1]);
            Advancement advancement = Bukkit.getAdvancement(key);

            if (advancement == null) {
                plugin.getLogger().warning("Advancement not found: " + id);
                return;
            }

            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            if (!progress.isDone()) {
                progress.getRemainingCriteria().forEach(progress::awardCriteria);
                plugin.getLogger().info("Granted advancement '" + id + "' to player: " + player.getName());
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid NamespacedKey: " + id + " - " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Unexpected error while granting advancement: " + e.getMessage());
            e.printStackTrace();
        }
    }
}