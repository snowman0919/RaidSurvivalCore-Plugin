package com.example.raidsurvivalcore.protection;

import java.time.Duration;
import java.time.Instant;

public final class PlaytimeAccumulator {
    private Duration accumulated;
    private Instant sessionStart;

    public PlaytimeAccumulator(Duration accumulated) {
        this.accumulated = accumulated;
    }

    public void join(Instant now) {
        sessionStart = now;
    }

    public void leave(Instant now) {
        if (sessionStart != null) {
            accumulated = accumulated.plus(Duration.between(sessionStart, now));
            sessionStart = null;
        }
    }

    public Duration current(Instant now) {
        if (sessionStart == null) return accumulated;
        return accumulated.plus(Duration.between(sessionStart, now));
    }
}
