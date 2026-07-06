package com.example.raidsurvivalcore.listener;

import com.example.raidsurvivalcore.combat.CombatLogManager;
import com.example.raidsurvivalcore.config.RaidCoreConfig;
import com.example.raidsurvivalcore.protection.NewPlayerProtectionManager;
import com.example.raidsurvivalcore.protection.RespawnProtectionManager;
import com.example.raidsurvivalcore.spawn.RandomSpawnManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

public final class PlayerLifecycleListener implements Listener {
    private final Plugin plugin;
    private final NewPlayerProtectionManager newbie;
    private final RespawnProtectionManager respawn;
    private final CombatLogManager combatLog;
    private final RandomSpawnManager randomSpawn;
    private RaidCoreConfig config;

    public PlayerLifecycleListener(Plugin plugin, NewPlayerProtectionManager newbie, RespawnProtectionManager respawn, CombatLogManager combatLog, RandomSpawnManager randomSpawn, RaidCoreConfig config) {
        this.plugin = plugin;
        this.newbie = newbie;
        this.respawn = respawn;
        this.combatLog = combatLog;
        this.randomSpawn = randomSpawn;
        this.config = config;
    }

    public void reload(RaidCoreConfig config) {
        this.config = config;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        newbie.join(player);
        combatLog.handleJoin(player);
        if (!player.hasPlayedBefore() && config.randomSpawn().firstJoin()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> randomSpawn.randomTeleport(player), 20L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        combatLog.handleQuit(event.getPlayer());
        newbie.quit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        respawn.protect(event.getPlayer());
        if (config.randomSpawn().respawn() && !event.isBedSpawn() && !event.isAnchorSpawn()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> randomSpawn.randomTeleport(event.getPlayer()), 1L);
        }
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        World world = event.getPlayer().getWorld();
        newbie.handleWorld(event.getPlayer(), world.getEnvironment());
    }
}
