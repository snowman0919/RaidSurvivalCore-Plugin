package com.example.raidsurvivalcore.combat;

import java.time.Duration;
import java.time.Instant;

public final class CombatLogGrace {
    private final Instant quitAt;
    private final Duration grace;

    public CombatLogGrace(Instant quitAt, Duration grace) {
        this.quitAt = quitAt;
        this.grace = grace;
    }

    public boolean expired(Instant now) {
        return !quitAt.plus(grace).isAfter(now);
    }
}
