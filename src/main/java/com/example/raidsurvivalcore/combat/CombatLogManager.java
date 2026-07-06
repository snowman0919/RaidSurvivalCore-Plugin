package com.example.raidsurvivalcore.combat;

import com.example.raidsurvivalcore.config.RaidCoreConfig;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class CombatLogManager {
    private final Plugin plugin;
    private final CombatManager combatManager;
    private final Map<UUID, Integer> pendingTasks = new ConcurrentHashMap<>();
    private RaidCoreConfig config;
    private boolean shuttingDown;

    public CombatLogManager(Plugin plugin, CombatManager combatManager, RaidCoreConfig config) {
        this.plugin = plugin;
        this.combatManager = combatManager;
        this.config = config;
    }

    public void reload(RaidCoreConfig config) {
        this.config = config;
    }

    public void markShutdown() {
        shuttingDown = true;
        pendingTasks.values().forEach(Bukkit.getScheduler()::cancelTask);
        pendingTasks.clear();
    }

    public void handleQuit(Player player) {
        if (shuttingDown || !config.combatLog().enabled() || !combatManager.isTagged(player.getUniqueId())) return;
        if (player.hasPermission(config.combatLog().bypassPermission())) return;
        UUID uuid = player.getUniqueId();
        int task = Bukkit.getScheduler().runTaskLater(plugin, () -> punishIfStillOffline(uuid), Math.max(1, config.combatLog().reconnectGrace().toSeconds() * 20L)).getTaskId();
        pendingTasks.put(uuid, task);
    }

    public void handleJoin(Player player) {
        Integer task = pendingTasks.remove(player.getUniqueId());
        if (task != null) Bukkit.getScheduler().cancelTask(task);
    }

    private void punishIfStillOffline(UUID uuid) {
        pendingTasks.remove(uuid);
        Player online = Bukkit.getPlayer(uuid);
        if (online != null || shuttingDown) return;
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        plugin.getLogger().info("Combat log punishment recorded for " + offline.getName() + " at " + Instant.now() + ". Offline inventory drop requires server-specific restore hooks; tag cleared.");
        combatManager.clear(uuid);
    }
}
