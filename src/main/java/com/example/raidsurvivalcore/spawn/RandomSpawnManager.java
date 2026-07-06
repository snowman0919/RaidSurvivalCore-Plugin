package com.example.raidsurvivalcore.spawn;

import com.example.raidsurvivalcore.config.MessageService;
import com.example.raidsurvivalcore.config.RaidCoreConfig;
import com.example.raidsurvivalcore.territory.TerritoryService;
import java.util.Map;
import java.util.SplittableRandom;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class RandomSpawnManager {
    private final Plugin plugin;
    private final MessageService messages;
    private final SplittableRandom random = new SplittableRandom();
    private RaidCoreConfig config;
    private TerritoryService territory;

    public RandomSpawnManager(Plugin plugin, RaidCoreConfig config, MessageService messages) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
    }

    public void reload(RaidCoreConfig config) {
        this.config = config;
    }

    public void setTerritoryService(TerritoryService territory) {
        this.territory = territory;
    }

    public void randomTeleport(Player player) {
        if (!config.randomSpawn().enabled() || player.hasPermission("raidcore.randomspawn.bypass")) return;
        World world = Bukkit.getWorld(config.randomSpawn().world());
        if (world == null) world = Bukkit.getWorlds().getFirst();
        find(player, world, 0);
    }

    public Location fallback() {
        World world = Bukkit.getWorld(config.randomSpawn().fallbackWorld());
        if (world == null) world = Bukkit.getWorlds().getFirst();
        return new Location(world, config.randomSpawn().fallbackX(), config.randomSpawn().fallbackY(), config.randomSpawn().fallbackZ(), config.randomSpawn().fallbackYaw(), config.randomSpawn().fallbackPitch());
    }

    private void find(Player player, World world, int attempt) {
        if (attempt >= config.randomSpawn().maxAttempts()) {
            player.sendMessage(messages.prefixed("random-spawn-failed", Map.of()));
            player.teleportAsync(fallback());
            return;
        }
        RandomSpawnRules.Offset offset = RandomSpawnRules.uniformOffset(random, config.randomSpawn().minRadius(), config.randomSpawn().maxRadius());
        int x = (int) Math.round(config.randomSpawn().centerX() + offset.x());
        int z = (int) Math.round(config.randomSpawn().centerZ() + offset.z());
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> Bukkit.getScheduler().runTask(plugin, () -> {
            Location candidate = safeLocation(world, x, z);
            if (candidate != null && farFromPlayers(candidate)) {
                player.teleportAsync(candidate);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () -> find(player, world, attempt + 1), 1L);
            }
        }));
    }

    private Location safeLocation(World world, int x, int z) {
        if (!world.getWorldBorder().isInside(new Location(world, x, world.getMaxHeight() - 1, z))) return null;
        int y = world.getHighestBlockYAt(x, z);
        Location feet = new Location(world, x + 0.5, y + 1, z + 0.5);
        if (config.randomSpawn().forbiddenBiomes().contains(feet.getBlock().getBiome().getKey().getKey().toLowerCase())) return null;
        if (territory != null && territory.nearAnyCore(world.getUID(), x, z, 256)) return null;
        Block ground = world.getBlockAt(x, y, z);
        Block foot = world.getBlockAt(x, y + 1, z);
        Block head = world.getBlockAt(x, y + 2, z);
        if (!ground.getType().isSolid()) return null;
        if (!foot.isPassable() || !head.isPassable()) return null;
        if (foot.isLiquid() || head.isLiquid()) return null;
        if (hazard(ground.getType()) || hazard(foot.getType()) || hazard(head.getType())) return null;
        if (world.rayTraceBlocks(feet, feet.getDirection(), 0.1, FluidCollisionMode.ALWAYS) != null) return null;
        return feet;
    }

    private boolean hazard(Material material) {
        return material == Material.LAVA || material == Material.FIRE || material == Material.SOUL_FIRE || material == Material.MAGMA_BLOCK
            || material == Material.CACTUS || material == Material.POWDER_SNOW || material == Material.SWEET_BERRY_BUSH;
    }

    private boolean farFromPlayers(Location location) {
        double minSq = config.randomSpawn().minDistanceFromPlayers() * config.randomSpawn().minDistanceFromPlayers();
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) < minSq) return false;
        }
        return true;
    }
}
