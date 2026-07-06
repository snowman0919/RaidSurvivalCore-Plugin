package com.example.raidsurvivalcore.listener;

import com.example.raidsurvivalcore.bounty.BountyManager;
import com.example.raidsurvivalcore.combat.CombatManager;
import com.example.raidsurvivalcore.config.MessageService;
import com.example.raidsurvivalcore.death.DeathLocationManager;
import com.example.raidsurvivalcore.protection.NewPlayerProtectionManager;
import com.example.raidsurvivalcore.protection.RespawnProtectionManager;
import com.example.raidsurvivalcore.proximity.ProximityManager;
import com.example.raidsurvivalcore.tribe.TribeService;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class CombatListener implements Listener {
    private final CombatManager combat;
    private final ProximityManager proximity;
    private final NewPlayerProtectionManager newbie;
    private final RespawnProtectionManager respawn;
    private final MessageService messages;
    private final DeathLocationManager deathLocation;
    private final BountyManager bounty;
    private final TribeService tribes;

    public CombatListener(CombatManager combat, ProximityManager proximity, NewPlayerProtectionManager newbie, RespawnProtectionManager respawn, MessageService messages, DeathLocationManager deathLocation, BountyManager bounty, TribeService tribes) {
        this.combat = combat;
        this.proximity = proximity;
        this.newbie = newbie;
        this.respawn = respawn;
        this.messages = messages;
        this.deathLocation = deathLocation;
        this.bounty = bounty;
        this.tribes = tribes;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (proximity.isProximityDamage(victim.getUniqueId())) return;
        Optional<Player> attacker = attacker(event.getDamager());
        if (attacker.isEmpty()) return;
        Player damager = attacker.get();
        if (damager.equals(victim)) return;
        if (tribes.snapshot().sameTribe(damager.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (newbie.protectedNow(victim) || respawn.protectedNow(victim)) {
            event.setCancelled(true);
            damager.sendMessage(messages.prefixed("target-protected", Map.of()));
            return;
        }
        if (newbie.protectedNow(damager)) {
            newbie.disable(damager);
            damager.sendMessage(messages.prefixed("protected-cannot-attack", Map.of()));
        }
        if (respawn.protectedNow(damager)) respawn.clear(damager);
        if (event.getFinalDamage() >= 1.0) {
            combat.tag(damager, victim);
            proximity.markValidAttack(damager, victim, event.getFinalDamage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event instanceof EntityDamageByEntityEvent) return;
        switch (event.getCause()) {
            case FIRE_TICK, POISON, WITHER, MAGIC, DRAGON_BREATH -> combat.lastAttacker(victim.getUniqueId()).map(org.bukkit.Bukkit::getPlayer).ifPresent(attacker -> combat.tag(attacker, victim));
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        deathLocation.record(victim);
        UUID killer = victim.getKiller() != null ? victim.getKiller().getUniqueId() : combat.lastAttacker(victim.getUniqueId()).orElse(null);
        bounty.handleKill(killer, victim.getUniqueId());
        tribes.handlePvpDeath(killer, victim.getUniqueId(), newbie.protectedNow(victim));
        combat.clear(victim.getUniqueId());
        proximity.clear(victim);
    }

    private Optional<Player> attacker(Entity damager) {
        if (damager instanceof Player player) return Optional.of(player);
        if (damager instanceof Projectile projectile) return source(projectile.getShooter());
        if (damager instanceof Firework firework) return source(firework.getShooter());
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) return Optional.of(player);
        if (damager instanceof Tameable tameable && tameable.getOwner() instanceof Player player) return Optional.of(player);
        return Optional.empty();
    }

    private Optional<Player> source(ProjectileSource source) {
        if (source instanceof Player player) return Optional.of(player);
        if (source instanceof Tameable tameable && tameable.getOwner() instanceof Player owner) return Optional.of(owner);
        return Optional.empty();
    }
}
