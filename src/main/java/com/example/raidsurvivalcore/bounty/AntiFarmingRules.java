package com.example.raidsurvivalcore.bounty;

public final class AntiFarmingRules {
    private AntiFarmingRules() {
    }

    public static double rewardMultiplier(int killsInWindow, int fullRewardKills, int noRewardFromKill, double reducedMultiplier) {
        int nextKill = killsInWindow + 1;
        if (nextKill >= noRewardFromKill) return 0.0;
        if (nextKill > fullRewardKills) return reducedMultiplier;
        return 1.0;
    }
}
