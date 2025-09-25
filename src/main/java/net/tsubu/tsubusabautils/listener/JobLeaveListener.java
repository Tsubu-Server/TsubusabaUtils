package net.tsubu.tsubusabautils.listener;

import com.gamingmesh.jobs.api.JobsLeaveEvent;
import net.tsubu.tsubusabautils.TsubusabaUtils;
import net.tsubu.tsubusabautils.manager.SidebarManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class JobLeaveListener implements Listener {

    private final TsubusabaUtils plugin;
    private final SidebarManager sidebarManager;

    public JobLeaveListener(TsubusabaUtils plugin, SidebarManager sidebarManager) {
        this.plugin = plugin;
        this.sidebarManager = sidebarManager;
    }

    @EventHandler
    public void onJobLeave(JobsLeaveEvent event) {
        Player player = event.getPlayer().getPlayer();
        if (player == null) return;

        String jobName = event.getJob().getDisplayName();
        sidebarManager.removeJob(player, jobName);
    }
}
