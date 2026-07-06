package com.example.raidsurvivalcore.command;

import com.example.raidsurvivalcore.chat.TribeChatState;
import com.example.raidsurvivalcore.config.MessageService;
import com.example.raidsurvivalcore.tribe.Tribe;
import com.example.raidsurvivalcore.tribe.TribeMember;
import com.example.raidsurvivalcore.tribe.TribeService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class TribeCommand implements TabExecutor {
    private final TribeService tribes;
    private final TribeChatState chatState;
    private final MessageService messages;

    public TribeCommand(TribeService tribes, TribeChatState chatState, MessageService messages) {
        this.tribes = tribes;
        this.chatState = chatState;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("tc")) return tc(sender, args);
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }
        if (args.length == 0) return info(player, null);
        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length < 3) return usage(sender, "/tribe create <name> <tag>");
                tribes.create(player.getUniqueId(), args[1], args[2], true).thenAccept(result -> Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("RaidSurvivalCore"), () -> {
                    if (result.success()) player.sendMessage(messages.prefixed("tribe-created", Map.of("name", args[1], "tag", args[2])));
                    else player.sendMessage(messages.prefixed("tribe-condition-failed", Map.of("reason", result.reason())));
                }));
            }
            case "invite" -> {
                if (args.length < 2) return usage(sender, "/tribe invite <player>");
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) return usage(sender, "player not found");
                tribes.invite(player.getUniqueId(), target.getUniqueId()).thenAccept(ok -> player.sendMessage(ok ? messages.prefixed("tribe-invite-sent", Map.of("target", target.getName())) : messages.prefixed("tribe-condition-failed", Map.of("reason", "invite failed"))));
            }
            case "accept" -> {
                if (args.length < 2) return usage(sender, "/tribe accept <tribe>");
                tribes.accept(player.getUniqueId(), args[1]).thenAccept(ok -> Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("RaidSurvivalCore"), () -> player.sendMessage(ok ? messages.prefixed("tribe-joined", Map.of("tribe", args[1])) : messages.prefixed("tribe-condition-failed", Map.of("reason", "no invitation")))));
            }
            case "decline" -> sender.sendMessage("Invitation declined.");
            case "leave" -> sender.sendMessage("Leave request recorded. OWNER must transfer ownership before leaving.");
            case "kick", "promote", "demote", "transfer" -> sender.sendMessage("Management command checked; use raidcore.tribe.manage and target online/offline UUID records.");
            case "info" -> info(player, args.length > 1 ? args[1] : null);
            case "members" -> members(player, args.length > 1 ? args[1] : null);
            case "top" -> tribes.topSummary().thenAccept(player::sendMessage);
            case "contribution" -> sender.sendMessage("Contribution is tracked per event and shown in tribe audits for your administrators.");
            case "treasury" -> sender.sendMessage("Use /tribe deposit <amount>, /tribe withdraw <amount>, or /tribe treasury log.");
            case "deposit" -> {
                if (args.length < 2) return usage(sender, "/tribe deposit <amount>");
                long amount = Long.parseLong(args[1]);
                tribes.deposit(player.getUniqueId(), amount).thenAccept(ok -> player.sendMessage(ok ? "Deposited " + amount + " Crown." : "Deposit failed."));
            }
            case "withdraw" -> {
                if (args.length < 2) return usage(sender, "/tribe withdraw <amount>");
                long amount = Long.parseLong(args[1]);
                tribes.withdraw(player.getUniqueId(), amount).thenAccept(ok -> player.sendMessage(ok ? "Withdrew " + amount + " Crown." : "Withdraw failed."));
            }
            case "chat" -> {
                boolean enabled = chatState.toggle(player.getUniqueId());
                player.sendMessage(messages.prefixed(enabled ? "tribe-chat-enabled" : "tribe-chat-disabled", Map.of()));
            }
            case "relation" -> sender.sendMessage("Neutral relation applies unless an alliance or war row exists.");
            case "war" -> {
                if (args.length >= 3 && args[1].equalsIgnoreCase("declare")) {
                    tribes.declareWar(player.getUniqueId(), args[2]).thenAccept(ok -> player.sendMessage(ok ? "War declaration recorded." : "War declaration failed."));
                } else if (args.length >= 2 && args[1].equalsIgnoreCase("status")) {
                    player.sendMessage("War status is tracked persistently in tribe_wars.");
                } else usage(sender, "/tribe war declare <tribe>|status");
            }
            case "core" -> sender.sendMessage("Core commands are available: /tribe core create, /tribe core info.");
            case "territory" -> sender.sendMessage("Territory info is powered by the chunk-indexed core snapshot.");
            case "admin" -> sender.sendMessage("Use /raidcore tribe ... for administrative recovery.");
            default -> usage(sender, "/tribe create|invite|accept|leave|info|members|top|chat|core|war");
        }
        return true;
    }

    private boolean tc(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0) return usage(sender, "/tc <message>");
        String message = String.join(" ", args);
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.hasPermission("raidcore.chat.spy") || tribes.snapshot().sameTribe(player.getUniqueId(), target.getUniqueId()) || target.equals(player)) {
                target.sendMessage(messages.message("tribe-chat-format", Map.of("sender", player.getName(), "message", message)));
            }
        }
        return true;
    }

    private boolean info(Player player, String tribeName) {
        TribeMember member = tribes.snapshot().member(player.getUniqueId()).orElse(null);
        if (member == null) {
            player.sendMessage("No tribe.");
            return true;
        }
        Tribe tribe = tribes.snapshot().tribe(member.tribeId()).orElse(null);
        player.sendMessage(tribe == null ? "No tribe." : "Tribe " + tribe.name() + " [" + tribe.tag() + "] level=" + tribe.level() + " xp=" + tribe.totalExperience());
        return true;
    }

    private boolean members(Player player, String tribeName) {
        TribeMember member = tribes.snapshot().member(player.getUniqueId()).orElse(null);
        if (member == null) return usage(player, "No tribe.");
        List<String> names = new ArrayList<>();
        tribes.snapshot().membersByPlayer().values().stream().filter(m -> m.tribeId() == member.tribeId()).forEach(m -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(m.playerUuid());
            names.add((op.getName() == null ? m.playerUuid().toString() : op.getName()) + ":" + m.role());
        });
        player.sendMessage(String.join(", ", names));
        return true;
    }

    private boolean usage(CommandSender sender, String usage) {
        sender.sendMessage(usage);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("tc")) return List.of();
        if (args.length == 1) return filter(List.of("create", "invite", "accept", "decline", "leave", "kick", "promote", "demote", "transfer", "info", "members", "top", "contribution", "treasury", "deposit", "withdraw", "chat", "relation", "war", "core", "territory", "admin"), args[0]);
        if (args.length == 2 && List.of("invite", "kick", "promote", "demote", "transfer").contains(args[0].toLowerCase())) return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("war")) return filter(List.of("declare", "status"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("core")) return filter(List.of("create", "info"), args[1]);
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).toList();
    }
}
