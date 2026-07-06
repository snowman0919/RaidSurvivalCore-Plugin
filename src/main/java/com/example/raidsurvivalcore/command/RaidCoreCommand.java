package com.example.raidsurvivalcore.command;

import com.example.raidsurvivalcore.bounty.BountyManager;
import com.example.raidsurvivalcore.combat.CombatManager;
import com.example.raidsurvivalcore.config.ConfigService;
import com.example.raidsurvivalcore.config.MessageService;
import com.example.raidsurvivalcore.economy.CurrencyReason;
import com.example.raidsurvivalcore.economy.EconomyService;
import com.example.raidsurvivalcore.protection.NewPlayerProtectionManager;
import com.example.raidsurvivalcore.proximity.ProximityManager;
import com.example.raidsurvivalcore.spawn.RandomSpawnManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class RaidCoreCommand implements TabExecutor {
    private final Runnable reload;
    private final CombatManager combat;
    private final ProximityManager proximity;
    private final RandomSpawnManager randomSpawn;
    private final BountyManager bounty;
    private final NewPlayerProtectionManager newbie;
    private final MessageService messages;
    private final EconomyService economy;

    public RaidCoreCommand(Runnable reload, CombatManager combat, ProximityManager proximity, RandomSpawnManager randomSpawn, BountyManager bounty, NewPlayerProtectionManager newbie, MessageService messages, EconomyService economy) {
        this.reload = reload;
        this.combat = combat;
        this.proximity = proximity;
        this.randomSpawn = randomSpawn;
        this.bounty = bounty;
        this.newbie = newbie;
        this.messages = messages;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("bounty")) return bountyCommand(sender, args);
        if (args.length == 0) {
            sender.sendMessage("/raidcore reload|status|combat|proximity|spawn|bounty|protection|debug");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!sender.hasPermission("raidcore.reload")) return noPerm(sender);
                reload.run();
                sender.sendMessage(messages.prefixed("reload", Map.of()));
            }
            case "status" -> {
                Player target = args.length > 1 ? Bukkit.getPlayer(args[1]) : sender instanceof Player p ? p : null;
                if (target == null) return usage(sender, "/raidcore status <player>");
                sender.sendMessage("combat=" + combat.isTagged(target.getUniqueId()) + ", newbie=" + newbie.protectedNow(target) + ", proximity=" + proximity.debug(target));
            }
            case "combat" -> combatSub(sender, args);
            case "proximity" -> {
                Player target = args.length > 2 ? Bukkit.getPlayer(args[2]) : null;
                if (target == null) return usage(sender, "/raidcore proximity clear <player>");
                proximity.clear(target);
                sender.sendMessage("proximity cleared");
            }
            case "spawn" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("test") && sender instanceof Player p) randomSpawn.randomTeleport(p);
                else {
                    Player target = args.length > 2 ? Bukkit.getPlayer(args[2]) : null;
                    if (target == null) return usage(sender, "/raidcore spawn random <player>");
                    randomSpawn.randomTeleport(target);
                }
            }
            case "bounty" -> {
                if (args.length > 2 && args[1].equalsIgnoreCase("clear")) {
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
                    bounty.clear(target.getUniqueId());
                    sender.sendMessage("bounty cleared");
                }
            }
            case "protection" -> {
                if (args.length > 2 && args[1].equalsIgnoreCase("clear")) {
                    Player target = Bukkit.getPlayer(args[2]);
                    if (target != null) newbie.disable(target);
                    sender.sendMessage("protection cleared");
                }
            }
            case "debug" -> {
                Player target = args.length > 2 ? Bukkit.getPlayer(args[2]) : null;
                if (target != null) sender.sendMessage(proximity.debug(target));
            }
            case "economy" -> economySub(sender, args);
            case "tribe" -> sender.sendMessage("Tribe admin recovery uses SQLite audit tables; docs/ADMIN_RECOVERY.md lists exact recovery procedures.");
            default -> sender.sendMessage("/raidcore reload|status|combat|proximity|spawn|bounty|protection|debug");
        }
        return true;
    }

    private void economySub(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raidcore.economy.admin")) {
            noPerm(sender);
            return;
        }
        if (args.length < 3) {
            usage(sender, "/raidcore economy balance|add|remove|set <player> [amount] [reason]");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (args[1].equalsIgnoreCase("balance")) {
            economy.balance(target.getUniqueId()).thenAccept(balance -> sender.sendMessage(target.getName() + ": " + balance + " Crown"));
            return;
        }
        if (args.length < 5) {
            usage(sender, "/raidcore economy add|remove|set <player> <amount> <reason>");
            return;
        }
        long amount = Long.parseLong(args[3]);
        CurrencyReason reason = CurrencyReason.valueOf(args[4].toUpperCase());
        java.util.UUID actor = sender instanceof Player player ? player.getUniqueId() : null;
        String mode = args[1].toLowerCase();
        economy.adminAdjust(actor, target.getUniqueId(), amount, reason, mode).thenAccept(ok -> sender.sendMessage(ok ? "Economy updated." : "Economy update failed."));
    }

    private boolean bountyCommand(CommandSender sender, String[] args) {
        if (args.length == 0) return usage(sender, "/bounty set <player> <amount>|list|top|info");
        if (args[0].equalsIgnoreCase("set") && sender instanceof Player player && args.length >= 3) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            bounty.addBounty(player, target, Long.parseLong(args[2]));
            return true;
        }
        if (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("top")) {
            bounty.listTop().thenAccept(rows -> sender.sendMessage(String.join("\n", rows.isEmpty() ? List.of("No bounties") : rows)));
            return true;
        }
        if (args[0].equalsIgnoreCase("info") && args.length >= 2) {
            sender.sendMessage("Use /bounty top for stored bounty list.");
            return true;
        }
        return usage(sender, "/bounty set <player> <amount>|list|top|info <player>");
    }

    private void combatSub(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, "/raidcore combat clear|set <player> [seconds]");
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage("player not found");
            return;
        }
        if (args[1].equalsIgnoreCase("clear")) combat.clear(target.getUniqueId());
        if (args[1].equalsIgnoreCase("set")) combat.set(target.getUniqueId(), args.length > 3 ? Integer.parseInt(args[3]) : 20);
        sender.sendMessage("combat updated");
    }

    private boolean noPerm(CommandSender sender) {
        sender.sendMessage(messages.prefixed("no-permission", Map.of()));
        return true;
    }

    private boolean usage(CommandSender sender, String usage) {
        sender.sendMessage(usage);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("bounty")) return switch (args.length) {
            case 1 -> filter(List.of("set", "list", "top", "info"), args[0]);
            case 2 -> filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            default -> List.of();
        };
        if (args.length == 1) return filter(List.of("reload", "status", "combat", "proximity", "spawn", "bounty", "protection", "debug", "economy", "tribe"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("economy")) return filter(List.of("balance", "add", "remove", "set"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("combat")) return filter(List.of("clear", "set"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) return filter(List.of("random", "test"), args[1]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("proximity") || args[0].equalsIgnoreCase("bounty") || args[0].equalsIgnoreCase("protection"))) return filter(List.of("clear"), args[1]);
        if (args.length >= 2) return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[args.length - 1]);
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String option : options) if (option.toLowerCase().startsWith(lower)) out.add(option);
        return out;
    }
}
