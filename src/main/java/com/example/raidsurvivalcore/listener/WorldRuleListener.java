package com.example.raidsurvivalcore.listener;

import com.example.raidsurvivalcore.config.RaidCoreConfig;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

public final class WorldRuleListener implements Listener {
    private RaidCoreConfig config;

    public WorldRuleListener(RaidCoreConfig config) {
        this.config = config;
    }

    public void reload(RaidCoreConfig config) {
        this.config = config;
        applyAll();
    }

    public void applyAll() {
        for (World world : Bukkit.getWorlds()) apply(world);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        apply(event.getWorld());
    }

    private void apply(World world) {
        if (config.advancements().disableAnnouncements()) world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
    }
}
