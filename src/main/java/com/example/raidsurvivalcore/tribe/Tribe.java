package com.example.raidsurvivalcore.tribe;

import java.time.Instant;
import java.util.UUID;

public record Tribe(
    long id,
    String name,
    String normalizedName,
    String tag,
    UUID ownerUuid,
    int level,
    long totalExperience,
    long spendableCurrency,
    long lockedCurrency,
    Instant createdAt,
    Instant updatedAt,
    String disbandState
) {
}
