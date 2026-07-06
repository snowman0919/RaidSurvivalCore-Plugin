package com.example.raidsurvivalcore.chat;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TribeChatState {
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public boolean toggle(UUID uuid) {
        if (enabled.remove(uuid)) return false;
        enabled.add(uuid);
        return true;
    }

    public boolean enabled(UUID uuid) {
        return enabled.contains(uuid);
    }
}
