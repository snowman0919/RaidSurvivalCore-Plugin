package com.example.raidsurvivalcore.combat;

public final class HealingRules {
    private HealingRules() {
    }

    public static double adjustedAmount(double originalAmount, double multiplier) {
        if (multiplier <= 0.0) return 0.0;
        return originalAmount * multiplier;
    }
}
