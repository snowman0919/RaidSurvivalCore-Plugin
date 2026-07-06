package com.example.raidsurvivalcore.spawn;

import java.util.random.RandomGenerator;

public final class RandomSpawnRules {
    private RandomSpawnRules() {
    }

    public record Offset(double x, double z, double radius) {
    }

    public static Offset uniformOffset(RandomGenerator random, double minRadius, double maxRadius) {
        double min2 = minRadius * minRadius;
        double max2 = maxRadius * maxRadius;
        double r = Math.sqrt(random.nextDouble(min2, max2));
        double theta = random.nextDouble(0.0, Math.PI * 2.0);
        return new Offset(r * Math.cos(theta), r * Math.sin(theta), r);
    }
}
