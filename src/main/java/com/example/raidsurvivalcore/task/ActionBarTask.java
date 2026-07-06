package com.example.raidsurvivalcore.task;

import com.example.raidsurvivalcore.combat.CombatManager;
import com.example.raidsurvivalcore.config.MessageService;
import com.example.raidsurvivalcore.protection.NewPlayerProtectionManager;
import com.example.raidsurvivalcore.protection.RespawnProtectionManager;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class ActionBarTask {
    private final Plugin plugin;
    private final CombatManager combat;
    private final NewPlayerProtectionManager newbie;
    private final RespawnProtectionManager respawn;
    private final MessageService messages;
    private int taskId = -1;

    public ActionBarTask(Plugin plugin, CombatManager combat, NewPlayerProtectionManager newbie, RespawnProtectionManager respawn, MessageService messages) {
        this.plugin = plugin;
        this.combat = combat;
        this.newbie = newbie;
        this.respawn = respawn;
        this.messages = messages;
    }

    public void start(long intervalTicks) {
        stop();
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks).getTaskId();
    }

    public void stop() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (combat.isTagged(player.getUniqueId())) {
                player.sendActionBar(messages.message("combat-actionbar", Map.of("seconds", String.valueOf(combat.remainingSeconds(player.getUniqueId())))));
            } else if (newbie.protectedNow(player)) {
                player.sendActionBar(messages.message("newbie-actionbar", Map.of("minutes", String.valueOf(newbie.remainingMinutes(player)))));
            } else if (respawn.protectedNow(player)) {
                player.sendActionBar(messages.message("respawn-actionbar", Map.of("seconds", String.valueOf(respawn.remainingSeconds(player)))));
            }
        }
    }
}
