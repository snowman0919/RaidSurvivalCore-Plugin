package com.example.raidsurvivalcore.tribe;

public final class TribeLevelRules {
    private TribeLevelRules() {
    }

    public static long requiredTotalXp(int level, double base, double exponent) {
        if (level <= 1) return 0;
        return Math.round(base * Math.pow(level, exponent));
    }

    public static int levelForXp(long totalXp, int maxLevel, double base, double exponent) {
        int level = 1;
        for (int next = 2; next <= maxLevel; next++) {
            if (totalXp < requiredTotalXp(next, base, exponent)) break;
            level = next;
        }
        return level;
    }

    public static long clampTransfer(long defenderXp, double ratio, long min, long max) {
        if (defenderXp <= 0) return 0;
        long computed = Math.round(defenderXp * ratio);
        return Math.max(0, Math.min(max, Math.max(min, computed)));
    }
}
