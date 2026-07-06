package com.example.raidsurvivalcore.proximity;

import java.time.Duration;
import java.time.Instant;

public final class ProximityRules {
    private ProximityRules() {
    }

    public static int stage(Duration elapsed, Duration grace, Duration interval) {
        long activeSeconds = elapsed.toSeconds() - grace.toSeconds();
        if (activeSeconds < 0) return -1;
        return (int) Math.floor((double) activeSeconds / Math.max(1, interval.toSeconds()));
    }

    public static double damage(Duration elapsed, Duration grace, Duration interval, double baseDamage, double increasePerStage, double maxDamage) {
        int stage = stage(elapsed, grace, interval);
        if (stage < 0) return 0.0;
        return Math.min(maxDamage, baseDamage + increasePerStage * stage);
    }

    public static boolean attackExempt(Instant lastValidAttack, Duration exemption, Instant now) {
        return lastValidAttack != null && lastValidAttack.plus(exemption).isAfter(now);
    }

    public static boolean shouldResetAfterSeparation(Instant separatedSince, Duration resetAfter, Instant now) {
        return separatedSince != null && !separatedSince.plus(resetAfter).isAfter(now);
    }

    public static double multiplier(int nearbyNonCombatPlayers, double step, double maxMultiplier) {
        return Math.min(maxMultiplier, 1.0 + Math.max(0, nearbyNonCombatPlayers - 1) * step);
    }
}
