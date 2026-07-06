package com.example.raidsurvivalcore.model;

import java.util.Objects;
import java.util.UUID;

public record PlayerPair(UUID first, UUID second) {
    public PlayerPair {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first.equals(second)) {
            throw new IllegalArgumentException("pair requires two distinct UUIDs");
        }
        if (first.compareTo(second) > 0) {
            UUID tmp = first;
            first = second;
            second = tmp;
        }
    }

    public boolean contains(UUID uuid) {
        return first.equals(uuid) || second.equals(uuid);
    }

    public UUID other(UUID uuid) {
        if (first.equals(uuid)) return second;
        if (second.equals(uuid)) return first;
        throw new IllegalArgumentException("uuid is not in pair");
    }
}
