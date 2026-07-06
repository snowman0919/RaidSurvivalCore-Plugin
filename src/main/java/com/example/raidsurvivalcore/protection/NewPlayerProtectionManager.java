package com.example.raidsurvivalcore.protection;

import com.example.raidsurvivalcore.config.RaidCoreConfig;
import com.example.raidsurvivalcore.persistence.PlayerDataRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class NewPlayerProtectionManager {
    private final PlayerDataRepository repository;
    private final Map<UUID, PlaytimeAccumulator> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> disabled = new ConcurrentHashMap<>();
    private RaidCoreConfig config;

    public NewPlayerProtectionManager(PlayerDataRepository repository, RaidCoreConfig config) {
        this.repository = repository;
        this.config = config;
    }

    public void reload(RaidCoreConfig config) {
        this.config = config;
    }

    public void join(Player player) {
        repository.load(player.getUniqueId()).thenAccept(data -> {
            PlaytimeAccumulator acc = new PlaytimeAccumulator(data.newbiePlaytime());
            acc.join(Instant.now());
            sessions.put(player.getUniqueId(), acc);
        });
    }

    public void quit(Player player) {
        UUID uuid = player.getUniqueId();
        PlaytimeAccumulator acc = sessions.remove(uuid);
        if (acc != null) {
            acc.leave(Instant.now());
            repository.getOrCreate(uuid).setNewbiePlaytime(acc.current(Instant.now()));
            repository.unload(uuid);
        }
    }

    public boolean protectedNow(Player player) {
        if (!config.newbie().enabled() || player.hasPermission("raidcore.newbie.bypass") || disabled.containsKey(player.getUniqueId())) return false;
        PlaytimeAccumulator acc = sessions.get(player.getUniqueId());
        Duration played = acc == null ? repository.getOrCreate(player.getUniqueId()).newbiePlaytime() : acc.current(Instant.now());
        return played.compareTo(config.newbie().playtime()) < 0;
    }

    public long remainingMinutes(Player player) {
        PlaytimeAccumulator acc = sessions.get(player.getUniqueId());
        Duration played = acc == null ? repository.getOrCreate(player.getUniqueId()).newbiePlaytime() : acc.current(Instant.now());
        return Math.max(0, config.newbie().playtime().minus(played).toMinutes());
    }

    public void disable(Player player) {
        disabled.put(player.getUniqueId(), true);
    }

    public void handleWorld(Player player, World.Environment environment) {
        if (environment == World.Environment.NETHER && config.newbie().disableOnNether()) disable(player);
    }
}
