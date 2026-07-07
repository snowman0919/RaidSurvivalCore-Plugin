package com.example.raidsurvivalcore.economy;

public record EconomySettings(
    long maxPersonalBalance,
    double payTaxRate,
    long newAccountStartingBalance,
    long hourlyGeneralTargetMin,
    long hourlyGeneralTargetMax,
    long mobLowRollMin,
    long mobLowRollMax,
    long advancementTaskReward,
    long advancementGoalReward,
    long advancementChallengeReward
) {
}
