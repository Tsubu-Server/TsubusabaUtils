package net.tsubu.tsubusabautils.listener;

import net.tsubu.tsubusabautils.TsubusabaUtils;
import org.bukkit.advancement.Advancement;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.advancement.AdvancementProgress;

import com.gamingmesh.jobs.api.JobsLevelUpEvent;
import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.container.Job;
import com.gamingmesh.jobs.container.JobProgression;
import com.gamingmesh.jobs.container.JobsPlayer;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;

import java.util.List;
import java.util.Map;

public class JobLevelListener implements Listener {

    private final TsubusabaUtils plugin;
    private final LuckPerms luckPerms;

    public JobLevelListener(TsubusabaUtils plugin) {
        this.plugin = plugin;
        this.luckPerms = plugin.getServer().getServicesManager().load(LuckPerms.class);
    }

    @EventHandler
    public void onJobsLevelUp(JobsLevelUpEvent event) {
        Player player = event.getPlayer().getPlayer();
        if (player == null) return;

        String jobName = event.getJob().getName();
        int newLevel = event.getLevel();

        processGlobalRewards(player, newLevel);
        processJobSpecificRewards(player, jobName, newLevel);
        processOrRewards(player);
        processCombinationRewards(player);
        processJobSpecificAdvancements(player, jobName, newLevel);
        processOrAdvancements(player);
        processCombinationAdvancements(player);
    }

    /**
     * グローバル報酬を処理する（任意の職業でのレベル条件）
     */
    private void processGlobalRewards(Player player, int newLevel) {
        ConfigurationSection globalRewards = plugin.getConfig().getConfigurationSection("global-rewards");
        if (globalRewards == null) return;

        for (String levelKey : globalRewards.getKeys(false)) {
            try {
                int requiredLevel = Integer.parseInt(levelKey);
                if (newLevel >= requiredLevel) {
                    String group = globalRewards.getString(levelKey);
                    if (group != null && !group.isEmpty()) {
                        addGroup(player, group);
                    }
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid level key in global-rewards: " + levelKey);
            }
        }
    }

    /**
     * 職業ごとの報酬を処理する
     */
    private void processJobSpecificRewards(Player player, String jobName, int newLevel) {
        ConfigurationSection jobRewards = plugin.getConfig().getConfigurationSection("job-rewards." + jobName);
        if (jobRewards == null) return;

        for (String levelKey : jobRewards.getKeys(false)) {
            try {
                int requiredLevel = Integer.parseInt(levelKey);
                if (newLevel >= requiredLevel) {
                    String group = jobRewards.getString(levelKey);
                    if (group != null && !group.isEmpty()) {
                        addGroup(player, group);
                    }
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid level key in job-rewards." + jobName + ": " + levelKey);
            }
        }
    }

    /**
     * OR条件による報酬を処理する（いずれか一つでも満たせばOK）
     */
    private void processOrRewards(Player player) {
        ConfigurationSection orRewards = plugin.getConfig().getConfigurationSection("or-rewards");
        if (orRewards == null) return;

        for (String groupName : orRewards.getKeys(false)) {
            ConfigurationSection groupConfig = orRewards.getConfigurationSection(groupName);
            if (groupConfig == null) continue;

            int minJobsRequired = groupConfig.getInt("min_jobs_required", 1);
            if (getPlayerJobCount(player) < minJobsRequired) {
                continue;
            }

            List<?> conditions = groupConfig.getList("conditions");
            if (conditions == null) continue;

            boolean anyConditionMet = false;

            for (Object conditionObj : conditions) {
                if (!(conditionObj instanceof Map)) continue;

                @SuppressWarnings("unchecked")
                Map<String, Object> condition = (Map<String, Object>) conditionObj;

                String requiredJob = (String) condition.get("job");
                Object levelObj = condition.get("level");

                if (requiredJob == null) requiredJob = "any";

                if (levelObj == null) continue;

                int requiredLevel;
                try {
                    requiredLevel = Integer.parseInt(levelObj.toString());
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid level in OR condition: " + levelObj);
                    continue;
                }

                if (checkJobCondition(player, requiredJob, requiredLevel)) {
                    anyConditionMet = true;
                    break;
                }
            }

            if (anyConditionMet) {
                addGroup(player, groupName);
            }
        }
    }

    /**
     * AND条件による報酬を処理する（全て満たす必要がある）
     */
    private void processCombinationRewards(Player player) {
        ConfigurationSection combinationRewards = plugin.getConfig().getConfigurationSection("combination-rewards");
        if (combinationRewards == null) return;

        for (String groupName : combinationRewards.getKeys(false)) {
            ConfigurationSection groupConfig = combinationRewards.getConfigurationSection(groupName);
            if (groupConfig == null) continue;

            List<?> conditions = groupConfig.getList("conditions");
            if (conditions == null) continue;

            boolean allConditionsMet = true;

            for (Object conditionObj : conditions) {
                if (!(conditionObj instanceof Map)) continue;

                @SuppressWarnings("unchecked")
                Map<String, Object> condition = (Map<String, Object>) conditionObj;

                String requiredJob = (String) condition.get("job");
                Object levelObj = condition.get("level");

                if (requiredJob == null || levelObj == null) continue;

                int requiredLevel;
                try {
                    requiredLevel = Integer.parseInt(levelObj.toString());
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid level in combination condition: " + levelObj);
                    continue;
                }

                if (!checkJobCondition(player, requiredJob, requiredLevel)) {
                    allConditionsMet = false;
                    break;
                }
            }

            if (allConditionsMet) {
                addGroup(player, groupName);
            }
        }
    }

    /**
     * 職業別の進捗を処理する
     */
    private void processJobSpecificAdvancements(Player player, String jobName, int newLevel) {
        processAchievements(player, "achievements." + jobName, newLevel);
    }

    /**
     * OR条件による進捗を処理する
     */
    private void processOrAdvancements(Player player) {
        ConfigurationSection orAchievements = plugin.getConfig().getConfigurationSection("achievements.or");
        if (orAchievements == null) return;

        for (String rewardKey : orAchievements.getKeys(false)) {
            ConfigurationSection rewardConfig = plugin.getConfig().getConfigurationSection("or-rewards." + rewardKey);
            if (rewardConfig == null) continue;

            int minJobsRequired = rewardConfig.getInt("min_jobs_required", 1);
            if (getPlayerJobCount(player) < minJobsRequired) {
                continue;
            }

            List<?> conditions = rewardConfig.getList("conditions");
            if (conditions == null) continue;

            boolean anyConditionMet = false;

            for (Object conditionObj : conditions) {
                if (!(conditionObj instanceof Map)) continue;

                @SuppressWarnings("unchecked")
                Map<String, Object> condition = (Map<String, Object>) conditionObj;

                String requiredJob = (String) condition.get("job");
                Object levelObj = condition.get("level");

                if (requiredJob == null) requiredJob = "any";
                if (levelObj == null) continue;

                int requiredLevel;
                try {
                    requiredLevel = Integer.parseInt(levelObj.toString());
                } catch (NumberFormatException e) {
                    continue;
                }

                if (checkJobCondition(player, requiredJob, requiredLevel)) {
                    anyConditionMet = true;
                    break;
                }
            }

            if (anyConditionMet) {
                String advancementId = orAchievements.getString(rewardKey);
                if (advancementId != null && !advancementId.isEmpty()) {
                    grantAdvancement(player, advancementId);
                }
            }
        }
    }

    /**
     * AND条件による進捗を処理する
     */
    private void processCombinationAdvancements(Player player) {
        ConfigurationSection combinationAchievements = plugin.getConfig().getConfigurationSection("achievements.combination");
        if (combinationAchievements == null) return;

        for (String rewardKey : combinationAchievements.getKeys(false)) {
            ConfigurationSection rewardConfig = plugin.getConfig().getConfigurationSection("combination-rewards." + rewardKey);
            if (rewardConfig == null) continue;

            List<?> conditions = rewardConfig.getList("conditions");
            if (conditions == null) continue;

            boolean allConditionsMet = true;

            for (Object conditionObj : conditions) {
                if (!(conditionObj instanceof Map)) continue;

                @SuppressWarnings("unchecked")
                Map<String, Object> condition = (Map<String, Object>) conditionObj;

                String requiredJob = (String) condition.get("job");
                Object levelObj = condition.get("level");

                if (requiredJob == null || levelObj == null) continue;

                int requiredLevel;
                try {
                    requiredLevel = Integer.parseInt(levelObj.toString());
                } catch (NumberFormatException e) {
                    continue;
                }

                if (!checkJobCondition(player, requiredJob, requiredLevel)) {
                    allConditionsMet = false;
                    break;
                }
            }

            if (allConditionsMet) {
                String advancementId = combinationAchievements.getString(rewardKey);
                if (advancementId != null && !advancementId.isEmpty()) {
                    grantAdvancement(player, advancementId);
                }
            }
        }
    }

    /**
     * 指定されたセクションの進捗を処理する
     */
    private void processAchievements(Player player, String configPath, int currentLevel) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(configPath);
        if (section == null) return;

        section.getKeys(false).forEach(levelKey -> {
            try {
                int targetLevel = Integer.parseInt(levelKey);
                if (currentLevel >= targetLevel) {
                    String advancementId = section.getString(levelKey);
                    if (advancementId != null && !advancementId.isEmpty()) {
                        grantAdvancement(player, advancementId);
                    }
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid level key in config: " + levelKey + " in section: " + configPath);
            }
        });
    }

    /**
     * プレイヤーの職業数を取得する
     */
    private int getPlayerJobCount(Player player) {
        try {
            JobsPlayer jobsPlayer = Jobs.getPlayerManager().getJobsPlayer(player);
            if (jobsPlayer == null) return 0;

            int count = 0;
            for (JobProgression progression : jobsPlayer.getJobProgression()) {
                if (progression != null && progression.getLevel() > 0) {
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
     * 特定の職業条件をチェックする
     */
    private boolean checkJobCondition(Player player, String jobName, int requiredLevel) {
        try {
            JobsPlayer jobsPlayer = Jobs.getPlayerManager().getJobsPlayer(player);

            if (jobsPlayer == null) {
                plugin.getLogger().warning("JobsPlayer not found for: " + player.getName());
                return false;
            }

            if ("any".equalsIgnoreCase(jobName)) {
                for (JobProgression progression : jobsPlayer.getJobProgression()) {
                    if (progression != null && progression.getLevel() >= requiredLevel) {
                        return true;
                    }
                }
                return false;
            } else {
                Job job = Jobs.getJob(jobName);
                if (job == null) {
                    plugin.getLogger().warning("Job not found: " + jobName);
                    return false;
                }

                JobProgression progression = jobsPlayer.getJobProgression(job);
                if (progression != null) {
                    return progression.getLevel() >= requiredLevel;
                } else {
                    return false;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error checking job condition for player " + player.getName() +
                    ", job: " + jobName + ", level: " + requiredLevel);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * グループを追加する
     */
    private void addGroup(Player player, String groupName) {
        if (groupName == null || groupName.isEmpty()) return;
        if (luckPerms == null) {
            plugin.getLogger().warning("LuckPerms is not available!");
            return;
        }

        User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
        InheritanceNode node = InheritanceNode.builder(groupName).build();

        // 既にグループを持っているかチェック
        boolean hasGroup = user.data().toCollection().stream()
                .anyMatch(n -> n.equals(node));

        if (!hasGroup) {
            user.data().add(node);
            luckPerms.getUserManager().saveUser(user);
            player.sendMessage("§b追加の職業枠が解放されました！");
        }
    }

    /**
     * プレイヤーに進捗を付与する
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
                // 全ての基準を達成させる
                progress.getRemainingCriteria().forEach(progress::awardCriteria);
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid NamespacedKey: " + id + " - " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Unexpected error while granting advancement: " + e.getMessage());
            e.printStackTrace();
        }
    }
}