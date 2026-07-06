package com.example.raidsurvivalcore.tribe;

import java.time.Instant;
import java.util.UUID;

public record TribeMember(
    long tribeId,
    UUID playerUuid,
    TribeRole role,
    Instant joinedAt,
    long contributionExperience,
    long contributionCurrency,
    int loyalty,
    Instant lastActiveAt,
    Instant transferCooldownUntil
) {
    public boolean canManageTreasury() {
        return role == TribeRole.OWNER || role == TribeRole.OFFICER;
    }

    public boolean restrictedRecruit() {
        return role == TribeRole.RECRUIT || role == TribeRole.PRISONER;
    }
}
