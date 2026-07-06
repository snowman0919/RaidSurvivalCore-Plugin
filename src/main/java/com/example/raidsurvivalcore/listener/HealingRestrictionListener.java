package com.example.raidsurvivalcore.listener;

import com.example.raidsurvivalcore.combat.CombatManager;
import com.example.raidsurvivalcore.combat.HealingRules;
import com.example.raidsurvivalcore.config.RaidCoreConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.plugin.Plugin;

public final class HealingRestrictionListener implements Listener {
    private final Plugin plugin;
    private final CombatManager combat;
    private final Map<UUID, Material> recentFoodHeal = new ConcurrentHashMap<>();
    private RaidCoreConfig config;

    public HealingRestrictionListener(Plugin plugin, CombatManager combat, RaidCoreConfig config) {
        this.plugin = plugin;
        this.combat = combat;
        this.config = config;
    }

    public void reload(RaidCoreConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Material type = event.getItem().getType();
        if (type == Material.GOLDEN_APPLE || type == Material.ENCHANTED_GOLDEN_APPLE) {
            recentFoodHeal.put(event.getPlayer().getUniqueId(), type);
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> recentFoodHeal.remove(event.getPlayer().getUniqueId()), 80L);
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> scaleAbsorption(event.getPlayer(), type == Material.ENCHANTED_GOLDEN_APPLE ? config.healing().enchantedGoldenApple() : config.healing().absorption()), 2L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player) || !combat.isTagged(player.getUniqueId())) return;
        double multiplier = multiplier(player, event.getRegainReason());
        double adjusted = HealingRules.adjustedAmount(event.getAmount(), multiplier);
        if (adjusted <= 0) event.setCancelled(true); else event.setAmount(adjusted);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTotem(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player && combat.isTagged(player.getUniqueId()) && !config.healing().allowTotem()) {
            event.setCancelled(true);
        }
    }

    private double multiplier(Player player, EntityRegainHealthEvent.RegainReason reason) {
        Material recent = recentFoodHeal.get(player.getUniqueId());
        if (recent == Material.ENCHANTED_GOLDEN_APPLE) return config.healing().enchantedGoldenApple();
        if (recent == Material.GOLDEN_APPLE) return config.healing().goldenApple();
        return switch (reason) {
            case SATIATED -> config.healing().saturation();
            case REGEN -> config.healing().natural();
            case MAGIC_REGEN -> config.healing().regeneration();
            case MAGIC -> config.healing().instantHealth();
            default -> 1.0;
        };
    }

    private void scaleAbsorption(Player player, double multiplier) {
        if (!combat.isTagged(player.getUniqueId())) return;
        var attribute = player.getAttribute(Attribute.MAX_ABSORPTION);
        if (attribute == null) return;
        player.setAbsorptionAmount(player.getAbsorptionAmount() * Math.max(0.0, Math.min(1.0, multiplier)));
    }
}
