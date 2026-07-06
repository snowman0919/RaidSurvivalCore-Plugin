package com.example.raidsurvivalcore.proximity;

import com.example.raidsurvivalcore.combat.CombatManager;
import com.example.raidsurvivalcore.config.MessageService;
import com.example.raidsurvivalcore.config.RaidCoreConfig;
import com.example.raidsurvivalcore.model.PlayerPair;
import com.example.raidsurvivalcore.protection.NewPlayerProtectionManager;
import com.example.raidsurvivalcore.tribe.TribeService;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class ProximityManager {
    private final Plugin plugin;
    private final CombatManager combatManager;
    private final NewPlayerProtectionManager newbie;
    private final MessageService messages;
    private TribeService tribes;
    private final Map<PlayerPair, ProximityState> states = new ConcurrentHashMap<>();
    private final Set<UUID> proximityDamage = ConcurrentHashMap.newKeySet();
    private RaidCoreConfig config;
    private int taskId = -1;

    public ProximityManager(Plugin plugin, RaidCoreConfig config, CombatManager combatManager, NewPlayerProtectionManager newbie, MessageService messages) {
        this.plugin = plugin;
        this.config = config;
        this.combatManager = combatManager;
        this.newbie = newbie;
        this.messages = messages;
    }

    public void setTribeService(TribeService tribes) {
        this.tribes = tribes;
    }

    public void reload(RaidCoreConfig config) {
        this.config = config;
        stop();
        start();
    }

    public void start() {
        if (!config.proximity().enabled()) return;
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::scan, config.proximity().scanTicks(), config.proximity().scanTicks()).getTaskId();
    }

    public void stop() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
    }

    public void markValidAttack(Player a, Player b, double finalDamage) {
        if (finalDamage < 1.0) return;
        states.computeIfAbsent(new PlayerPair(a.getUniqueId(), b.getUniqueId()), p -> new ProximityState(Instant.now())).markValidAttack(Instant.now());
    }

    public boolean isProximityDamage(UUID uuid) {
        return proximityDamage.remove(uuid);
    }

    public void clear(Player player) {
        states.keySet().removeIf(pair -> pair.contains(player.getUniqueId()));
    }

    public String debug(Player player) {
        long count = states.keySet().stream().filter(pair -> pair.contains(player.getUniqueId())).count();
        return "tracked pairs=" + count + ", eligible=" + eligible(player);
    }

    private void scan() {
        Instant now = Instant.now();
        Set<PlayerPair> seen = new HashSet<>();
        Map<UUID, Integer> nearbyCounts = new HashMap<>();
        double radius = config.proximity().radius();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!eligible(player)) continue;
            for (Entity entity : player.getNearbyEntities(radius, config.proximity().yThreshold(), radius)) {
                if (!(entity instanceof Player other) || !eligible(other)) continue;
                PlayerPair pair = new PlayerPair(player.getUniqueId(), other.getUniqueId());
                if (!seen.add(pair)) continue;
                if (qualifies(player, other)) {
                    nearbyCounts.merge(player.getUniqueId(), 1, Integer::sum);
                    nearbyCounts.merge(other.getUniqueId(), 1, Integer::sum);
                    handleClose(pair, now);
                } else {
                    handleSeparated(pair, now);
                }
            }
        }
        states.keySet().stream().filter(pair -> !seen.contains(pair)).toList().forEach(pair -> handleSeparated(pair, now));
        applyDamage(now, nearbyCounts);
    }

    private void handleClose(PlayerPair pair, Instant now) {
        ProximityState state = states.computeIfAbsent(pair, p -> new ProximityState(now));
        state.markSeen(now);
    }

    private void handleSeparated(PlayerPair pair, Instant now) {
        ProximityState state = states.get(pair);
        if (state == null) return;
        state.markSeparated(now);
        if (ProximityRules.shouldResetAfterSeparation(state.separatedSince(), config.proximity().resetAfterSeparated(), now)) {
            states.remove(pair);
        }
    }

    private void applyDamage(Instant now, Map<UUID, Integer> nearbyCounts) {
        for (Map.Entry<PlayerPair, ProximityState> entry : states.entrySet()) {
            ProximityState state = entry.getValue();
            if (ProximityRules.attackExempt(state.lastValidAttack(), config.proximity().attackExemption(), now)) continue;
            Duration elapsed = Duration.between(state.firstSeen(), now);
            double damage = ProximityRules.damage(elapsed, config.proximity().grace(), config.proximity().interval(), config.proximity().baseDamage(), config.proximity().increasePerStage(), config.proximity().maxDamage());
            if (damage <= 0) continue;
            if (state.lastDamage() != null && state.lastDamage().plus(config.proximity().interval()).isAfter(now)) continue;
            damagePlayer(entry.getKey().first(), damage, nearbyCounts);
            damagePlayer(entry.getKey().second(), damage, nearbyCounts);
            state.markDamage(now);
        }
    }

    private void damagePlayer(UUID uuid, double damage, Map<UUID, Integer> nearbyCounts) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !eligible(player)) return;
        double multiplier = ProximityRules.multiplier(nearbyCounts.getOrDefault(uuid, 1), config.proximity().extraStep(), config.proximity().maxMultiplier());
        proximityDamage.add(uuid);
        player.damage(damage * multiplier);
        player.sendActionBar(messages.message("proximity-damage", Map.of("damage", String.format("%.1f", damage * multiplier))));
    }

    private boolean qualifies(Player a, Player b) {
        Location la = a.getLocation();
        Location lb = b.getLocation();
        if (!la.getWorld().equals(lb.getWorld())) return false;
        if (tribes != null && tribes.snapshot().sameTribe(a.getUniqueId(), b.getUniqueId())) return false;
        if (Math.abs(la.getY() - lb.getY()) > config.proximity().yThreshold()) return false;
        double distanceSquared = la.distanceSquared(lb);
        if (distanceSquared > config.proximity().radius() * config.proximity().radius()) return false;
        if (distanceSquared <= config.proximity().alwaysDetectRadius() * config.proximity().alwaysDetectRadius()) return true;
        return a.hasLineOfSight(b) && b.hasLineOfSight(a);
    }

    private boolean eligible(Player player) {
        if (!player.isOnline() || player.isDead()) return false;
        if (player.hasPermission("raidcore.proximity.bypass")) return false;
        if (newbie.protectedNow(player)) return false;
        if (config.proximity().excludedWorlds().contains(player.getWorld().getName().toLowerCase())) return false;
        GameMode mode = player.getGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }
}
