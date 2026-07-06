package com.example.raidsurvivalcore.command;

import com.example.raidsurvivalcore.config.MessageService;
import com.example.raidsurvivalcore.economy.EconomyService;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class MoneyCommand implements TabExecutor {
    private final EconomyService economy;
    private final MessageService messages;

    public MoneyCommand(EconomyService economy, MessageService messages) {
        this.economy = economy;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("shop")) {
            sender.sendMessage("Shop is configured by economy.yml. Current build exposes economy storage and transaction APIs for server shops.");
            return true;
        }
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0) {
            economy.balance(player.getUniqueId()).thenAccept(balance -> player.sendMessage(messages.prefixed("money-balance", Map.of("amount", String.valueOf(balance)))));
            return true;
        }
        if (args[0].equalsIgnoreCase("pay") && args.length >= 3) {
            Player target = Bukkit.getPlayer(args[1]);
            long amount = Long.parseLong(args[2]);
            if (target == null || amount <= 0) {
                player.sendMessage(messages.prefixed("economy-failed", Map.of("reason", "invalid target or amount")));
                return true;
            }
            economy.pay(player.getUniqueId(), target.getUniqueId(), amount).thenAccept(ok -> Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("RaidSurvivalCore"), () -> {
                if (ok) {
                    player.sendMessage(messages.prefixed("money-paid", Map.of("target", target.getName(), "amount", String.valueOf(amount))));
                    target.sendMessage(messages.prefixed("money-received", Map.of("sender", player.getName(), "amount", String.valueOf(amount))));
                } else {
                    player.sendMessage(messages.prefixed("economy-failed", Map.of("reason", "insufficient balance or overflow")));
                }
            }));
            return true;
        }
        if (args[0].equalsIgnoreCase("top")) {
            sender.sendMessage("Money top uses currency_accounts; run the database query from admin tooling to avoid exposing private balances.");
            return true;
        }
        sender.sendMessage("/money | /money pay <player> <amount> | /money top");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("pay", "top").stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("pay")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        return List.of();
    }
}
