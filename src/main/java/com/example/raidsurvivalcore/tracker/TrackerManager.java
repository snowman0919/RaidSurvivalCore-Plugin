package com.example.raidsurvivalcore.tracker;

import com.example.raidsurvivalcore.config.RaidCoreConfig;
import com.example.raidsurvivalcore.persistence.PlayerDataRepository;
import com.example.raidsurvivalcore.protection.NewPlayerProtectionManager;
import java.util.Comparator;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.plugin.Plugin;

public final class TrackerManager {
    private final Plugin plugin;
    private final NewPlayerProtectionManager newbie;
    private final PlayerDataRepository repository;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private RaidCoreConfig config;
    private int taskId = -1;

    public TrackerManager(Plugin plugin, RaidCoreConfig config, NewPlayerProtectionManager newbie, PlayerDataRepository repository) {
        this.plugin = plugin;
        this.config = config;
        this.newbie = newbie;
        this.repository = repository;
    }

    public void reload(RaidCoreConfig config) {
        this.config = config;
        stop();
        start();
    }

    public void start() {
        if (!config.tracker().enabled()) return;
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 40L, Math.max(20L, config.tracker().updateInterval().toSeconds() * 20L)).getTaskId();
    }

    public void stop() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("raidcore.tracker.bypass")) continue;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null || item.getType() != Material.COMPASS) continue;
                updateCompass(player, item);
            }
        }
    }

    private void updateCompass(Player holder, ItemStack compass) {
        Player target = holder.getWorld().getPlayers().stream()
            .filter(p -> !p.equals(holder))
            .filter(p -> !p.hasPermission("raidcore.tracker.bypass"))
            .filter(p -> !newbie.protectedNow(p))
            .filter(p -> distance(holder, p) >= config.tracker().minDistance())
            .filter(p -> distance(holder, p) <= config.tracker().maxDistance())
            .min(Comparator.comparingDouble(p -> distance(holder, p)))
            .orElse(null);
        if (target == null) return;
        if (config.tracker().consumeToken() && !repository.getOrCreate(holder.getUniqueId()).spendTokens(config.tracker().tokenCost())) return;
        if (compass.getItemMeta() instanceof CompassMeta meta) {
            Location location = target.getLocation();
            meta.setLodestone(location);
            meta.setLodestoneTracked(false);
            meta.displayName(mini.deserialize(config.tracker().compassName()));
            meta.lore(java.util.List.of(mini.deserialize("<gray>대상 방향 추적 중</gray>"), mini.deserialize("<dark_gray>거리: " + Math.round(distance(holder, target)) + "m 범위</dark_gray>")));
            compass.setItemMeta(meta);
        }
    }

    private double distance(Player a, Player b) {
        return a.getLocation().distance(b.getLocation());
    }
}
