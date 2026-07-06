package com.example.raidsurvivalcore.combat;

import com.example.raidsurvivalcore.config.RaidCoreConfig;
import com.example.raidsurvivalcore.config.MessageService;
import com.example.raidsurvivalcore.util.TimeUtil;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class CombatManager {
    private final Map<UUID, CombatState> tagged = new ConcurrentHashMap<>();
    private final Map<UUID, LastAttacker> lastAttackers = new ConcurrentHashMap<>();
    private RaidCoreConfig config;
    private final MessageService messages;

    public CombatManager(RaidCoreConfig config, MessageService messages) {
        this.config = config;
        this.messages = messages;
    }

    public void reload(RaidCoreConfig config) {
        this.config = config;
    }

    public void tag(Player attacker, Player victim) {
        if (!config.combat().enabled()) return;
        if (attacker.hasPermission("raidcore.combat.bypass") || victim.hasPermission("raidcore.combat.bypass")) return;
        Instant expires = Instant.now().plus(config.combat().tagDuration());
        tagged.compute(attacker.getUniqueId(), (id, state) -> state == null ? new CombatState(expires, victim.getUniqueId()) : refresh(state, expires, victim.getUniqueId()));
        tagged.compute(victim.getUniqueId(), (id, state) -> state == null ? new CombatState(expires, attacker.getUniqueId()) : refresh(state, expires, attacker.getUniqueId()));
        rememberAttacker(victim.getUniqueId(), attacker.getUniqueId());
    }

    private CombatState refresh(CombatState state, Instant expires, UUID opponent) {
        state.refresh(expires, opponent);
        return state;
    }

    public void rememberAttacker(UUID victim, UUID attacker) {
        lastAttackers.put(victim, new LastAttacker(attacker, Instant.now().plus(config.combat().lastAttackerMemory())));
    }

    public Optional<UUID> lastAttacker(UUID victim) {
        LastAttacker last = lastAttackers.get(victim);
        if (last == null || TimeUtil.isExpired(last.expiresAt, Instant.now())) return Optional.empty();
        return Optional.of(last.attacker);
    }

    public boolean isTagged(UUID uuid) {
        CombatState state = tagged.get(uuid);
        return state != null && state.active(Instant.now());
    }

    public long remainingSeconds(UUID uuid) {
        CombatState state = tagged.get(uuid);
        return state == null ? 0 : TimeUtil.secondsRemaining(state.expiresAt(), Instant.now());
    }

    public void clear(UUID uuid) {
        tagged.remove(uuid);
    }

    public void set(UUID uuid, int seconds) {
        tagged.put(uuid, new CombatState(Instant.now().plusSeconds(seconds), null));
    }

    public void cleanupAndNotify() {
        Instant now = Instant.now();
        tagged.entrySet().removeIf(entry -> {
            if (entry.getValue().active(now)) return false;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) player.sendMessage(messages.prefixed("combat-ended", Map.of()));
            return true;
        });
        lastAttackers.entrySet().removeIf(entry -> TimeUtil.isExpired(entry.getValue().expiresAt, now));
    }

    private record LastAttacker(UUID attacker, Instant expiresAt) {
    }
}
