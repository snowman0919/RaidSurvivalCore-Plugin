package com.example.raidsurvivalcore.combat;

import java.time.Instant;
import java.util.UUID;

public final class CombatState {
    private Instant expiresAt;
    private UUID lastOpponent;

    public CombatState(Instant expiresAt, UUID lastOpponent) {
        this.expiresAt = expiresAt;
        this.lastOpponent = lastOpponent;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public UUID lastOpponent() {
        return lastOpponent;
    }

    public void refresh(Instant expiresAt, UUID opponent) {
        this.expiresAt = expiresAt;
        this.lastOpponent = opponent;
    }

    public boolean active(Instant now) {
        return expiresAt != null && expiresAt.isAfter(now);
    }
}
