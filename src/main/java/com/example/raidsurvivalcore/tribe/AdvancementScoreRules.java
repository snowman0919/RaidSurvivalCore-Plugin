package com.example.raidsurvivalcore.tribe;

import java.util.Map;

public final class AdvancementScoreRules {
    private AdvancementScoreRules() {
    }

    public enum Kind {
        TASK,
        GOAL,
        CHALLENGE,
        ROOT,
        RECIPE
    }

    public static int score(Kind kind, String key, boolean excludeRecipes, boolean excludeRoots, Map<String, Integer> bonus) {
        if (kind == Kind.RECIPE && excludeRecipes) return 0;
        if (kind == Kind.ROOT && excludeRoots) return 0;
        int base = switch (kind) {
            case TASK, ROOT, RECIPE -> 1;
            case GOAL -> 2;
            case CHALLENGE -> 5;
        };
        return base + Math.max(0, bonus.getOrDefault(key, 0));
    }
}
