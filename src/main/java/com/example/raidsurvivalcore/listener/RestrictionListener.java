package com.example.raidsurvivalcore.listener;

import com.example.raidsurvivalcore.combat.CombatManager;
import com.example.raidsurvivalcore.combat.CommandBlockRules;
import com.example.raidsurvivalcore.config.MessageService;
import com.example.raidsurvivalcore.config.RaidCoreConfig;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;

public final class RestrictionListener implements Listener {
    private final CombatManager combat;
    private final MessageService messages;
    private RaidCoreConfig config;

    public RestrictionListener(CombatManager combat, MessageService messages, RaidCoreConfig config) {
        this.combat = combat;
        this.messages = messages;
        this.config = config;
    }

    public void reload(RaidCoreConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!restricted(player)) return;
        if (CommandBlockRules.blocked(event.getMessage(), config.restrictions().blockedCommands(), config.restrictions().allowedCommands())) {
            event.setCancelled(true);
            player.sendMessage(messages.prefixed("combat-command-blocked", Map.of()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!restricted(event.getPlayer()) || event.getItem() == null) return;
        Material type = event.getItem().getType();
        if ((type == Material.CHORUS_FRUIT && config.restrictions().blockChorus())
            || (type == Material.FIREWORK_ROCKET && config.restrictions().blockFirework())
            || (type == Material.ENDER_PEARL && config.restrictions().blockEnderPearl())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicle(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player && restricted(player) && config.restrictions().blockVehicle()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (!restricted(event.getPlayer())) return;
        if ((event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL && config.restrictions().blockNetherPortal())
            || (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL && config.restrictions().blockEndPortal())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!restricted(event.getPlayer())) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL && config.restrictions().blockEnderPearl()) event.setCancelled(true);
    }

    private boolean restricted(Player player) {
        return config.restrictions().enabled() && combat.isTagged(player.getUniqueId()) && !player.hasPermission("raidcore.command.bypass");
    }
}
