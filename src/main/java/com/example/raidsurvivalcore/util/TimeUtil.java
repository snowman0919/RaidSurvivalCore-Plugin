package com.example.raidsurvivalcore.util;

import java.time.Duration;
import java.time.Instant;

public final class TimeUtil {
    private TimeUtil() {
    }

    public static boolean isExpired(Instant time, Instant now) {
        return time == null || !time.isAfter(now);
    }

    public static long secondsRemaining(Instant until, Instant now) {
        if (until == null) return 0;
        return Math.max(0, Duration.between(now, until).toSeconds());
    }
}
