package com.example.raidsurvivalcore.config;

import com.example.raidsurvivalcore.model.RegionBox;
import java.time.Duration;
import java.util.List;
import java.util.Set;

public record RaidCoreConfig(
    Proximity proximity,
    Combat combat,
    Healing healing,
    CombatLog combatLog,
    RandomSpawn randomSpawn,
    Newbie newbie,
    Respawn respawn,
    Death death,
    Bounty bounty,
    AntiFarming antiFarming,
    EnderChestLoot enderChestLoot,
    Advancements advancements,
    Tracker tracker,
    Database database,
    Performance performance,
    Restrictions restrictions
) {
    public record Proximity(boolean enabled, double radius, double yThreshold, double alwaysDetectRadius, Duration grace, Duration interval,
                            double baseDamage, double increasePerStage, double maxDamage, double extraStep, double maxMultiplier,
                            long scanTicks, Duration attackExemption, Duration resetAfterSeparated, Set<String> excludedWorlds) {
    }

    public record Combat(boolean enabled, Duration tagDuration, Duration lastAttackerMemory, boolean actionbar, List<RegionBox> safeZones) {
    }

    public record Healing(double natural, double saturation, double regeneration, double instantHealth, double goldenApple,
                          double enchantedGoldenApple, double beacon, double absorption, boolean allowTotem, boolean bypassPluginForcedHeal) {
    }

    public record CombatLog(boolean enabled, Duration reconnectGrace, String action, String bypassPermission) {
    }

    public record RandomSpawn(boolean enabled, boolean firstJoin, boolean respawn, String world, double centerX, double centerZ,
                              double minRadius, double maxRadius, int maxAttempts, double minDistanceFromPlayers,
                              String fallbackWorld, double fallbackX, double fallbackY, double fallbackZ, float fallbackYaw,
                              float fallbackPitch, Set<String> forbiddenBiomes) {
    }

    public record Newbie(boolean enabled, Duration playtime, boolean disableOnAttack, boolean disableOnNether) {
    }

    public record Respawn(boolean enabled, Duration duration, boolean disableOnAttack, boolean blockChest, boolean blockPickup, boolean blockPortal) {
    }

    public record Death(boolean enabled, boolean recoveryCompass, int itemDespawnMinutes, String timeZone) {
    }

    public record Bounty(boolean enabled, long minAmount, long maxAmount, boolean useVault, long internalStartingTokens) {
    }

    public record AntiFarming(boolean enabled, Duration window, int fullRewardKills, int reducedFromKill, int noRewardFromKill, double reducedMultiplier) {
    }

    public record EnderChestLoot(boolean enabled, boolean pvpOnly, double dropChance, int maxStacks, double stackFraction) {
    }

    public record Advancements(boolean disableAnnouncements) {
    }

    public record Tracker(boolean enabled, Duration updateInterval, double minDistance, double maxDistance, boolean consumeToken, long tokenCost,
                          String compassName) {
    }

    public record Database(String file, Duration autosave) {
    }

    public record Performance(long actionbarTicks, Duration cleanupInterval) {
    }

    public record Restrictions(boolean enabled, Set<String> blockedCommands, Set<String> allowedCommands, boolean blockChorus,
                               boolean blockVehicle, boolean blockNetherPortal, boolean blockEndPortal, boolean blockElytra,
                               boolean blockFirework, boolean blockEnderPearl) {
    }
}
