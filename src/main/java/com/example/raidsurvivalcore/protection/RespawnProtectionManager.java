package com.example.raidsurvivalcore.protection;

import com.example.raidsurvivalcore.config.RaidCoreConfig;
import com.example.raidsurvivalcore.util.TimeUtil;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class RespawnProtectionManager {
    private final Map<UUID, Instant> protectedUntil = new ConcurrentHashMap<>();
    private RaidCoreConfig config;

    public RespawnProtectionManager(RaidCoreConfig config) {
        this.config = config;
    }

    public void reload(RaidCoreConfig config) {
        this.config = config;
    }

    public void protect(Player player) {
        if (config.respawn().enabled() && !player.hasPermission("raidcore.respawnprotection.bypass")) {
            protectedUntil.put(player.getUniqueId(), Instant.now().plus(config.respawn().duration()));
        }
    }

    public boolean protectedNow(Player player) {
        Instant until = protectedUntil.get(player.getUniqueId());
        return until != null && until.isAfter(Instant.now());
    }

    public long remainingSeconds(Player player) {
        return TimeUtil.secondsRemaining(protectedUntil.get(player.getUniqueId()), Instant.now());
    }

    public void clear(Player player) {
        protectedUntil.remove(player.getUniqueId());
    }

    public void cleanup() {
        protectedUntil.entrySet().removeIf(e -> !e.getValue().isAfter(Instant.now()));
    }
}
