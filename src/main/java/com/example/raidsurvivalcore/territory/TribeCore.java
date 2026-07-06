package com.example.raidsurvivalcore.territory;

import java.time.Instant;
import java.util.UUID;

public record TribeCore(
    String coreId,
    long tribeId,
    UUID worldUuid,
    int x,
    int y,
    int z,
    int tier,
    double maxHealth,
    double currentHealth,
    CoreState state,
    Instant createdAt,
    Instant lastDamagedAt,
    Instant destroyedAt,
    int radius
) {
    public boolean activeProtection() {
        return state == CoreState.ACTIVE || state == CoreState.UNDER_ATTACK;
    }
}
