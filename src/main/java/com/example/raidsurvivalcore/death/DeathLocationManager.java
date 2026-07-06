package com.example.raidsurvivalcore.death;

import com.example.raidsurvivalcore.config.MessageService;
import com.example.raidsurvivalcore.config.RaidCoreConfig;
import com.example.raidsurvivalcore.persistence.PlayerDataRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;

public final class DeathLocationManager {
    private final PlayerDataRepository repository;
    private final MessageService messages;
    private RaidCoreConfig config;

    public DeathLocationManager(PlayerDataRepository repository, MessageService messages, RaidCoreConfig config) {
        this.repository = repository;
        this.messages = messages;
        this.config = config;
    }

    public void reload(RaidCoreConfig config) {
        this.config = config;
    }

    public void record(Player player) {
        if (!config.death().enabled()) return;
        Location l = player.getLocation();
        Instant now = Instant.now();
        repository.getOrCreate(player.getUniqueId()).setLastDeath(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), now);
        player.sendMessage(messages.prefixed("death-location", Map.of(
            "world", l.getWorld().getName(),
            "x", String.valueOf(l.getBlockX()),
            "y", String.valueOf(l.getBlockY()),
            "z", String.valueOf(l.getBlockZ()),
            "time", DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault()).format(now),
            "despawn", String.valueOf(config.death().itemDespawnMinutes())
        )));
        if (config.death().recoveryCompass()) updateCompass(player, l);
    }

    private void updateCompass(Player player, Location death) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != Material.RECOVERY_COMPASS) continue;
            if (item.getItemMeta() instanceof CompassMeta meta) {
                meta.setLodestone(death);
                meta.setLodestoneTracked(false);
                item.setItemMeta(meta);
            }
        }
    }
}
