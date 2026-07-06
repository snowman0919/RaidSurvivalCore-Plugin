package com.example.raidsurvivalcore.siege;

import java.time.Duration;
import java.time.Instant;

public final class SiegeRules {
    private SiegeRules() {
    }

    public static double coreMaxHealth(double base, double perLevel, int tribeLevel, int tier, double tierMultiplier) {
        return (base + perLevel * Math.max(1, tribeLevel)) * Math.max(1.0, tierMultiplier * Math.max(1, tier));
    }

    public static double applyDamageMultipliers(double baseDamage, boolean officialWar, int attackersNearby, int defendersOnline, double noWarMultiplier, double warMultiplier, double groupMultiplier, double noDefenderMultiplier) {
        double damage = baseDamage * (officialWar ? warMultiplier : noWarMultiplier);
        if (attackersNearby >= 3) damage *= groupMultiplier;
        if (defendersOnline <= 0) damage *= noDefenderMultiplier;
        return Math.max(0.0, damage);
    }

    public static boolean canRegen(Instant lastDamagedAt, Duration delay, Instant now, boolean underAttackOrBreached) {
        if (underAttackOrBreached || lastDamagedAt == null) return false;
        return !lastDamagedAt.plus(delay).isAfter(now);
    }

    public static long cappedLoot(long available, double ratio, long min, long max) {
        if (available <= 0) return 0;
        long computed = Math.round(available * Math.max(0.0, Math.min(1.0, ratio)));
        return Math.min(available, Math.min(max, Math.max(min, computed)));
    }
}
