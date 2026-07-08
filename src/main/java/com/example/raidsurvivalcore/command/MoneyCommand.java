package com.example.raidsurvivalcore.command;

import com.example.raidsurvivalcore.config.MessageService;
import com.example.raidsurvivalcore.economy.EconomyService;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MoneyCommand implements TabExecutor {
    private final JavaPlugin plugin;
    private final EconomyService economy;
    private final MessageService messages;

    public MoneyCommand(JavaPlugin plugin, EconomyService economy, MessageService messages) {
        this.plugin = plugin;
        this.economy = economy;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length == 0) {
            economy.balance(player.getUniqueId()).thenAccept(balance -> Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(messages.prefixed("money-balance", Map.of("amount", String.valueOf(balance))))));
            return true;
        }
        if (args[0].equalsIgnoreCase("pay") && args.length >= 3) {
            Player target = Bukkit.getPlayer(args[1]);
            Long amount = parseAmount(player, args[2]);
            if (amount == null) return true;
            if (target == null || amount <= 0) {
                player.sendMessage(messages.prefixed("economy-failed", Map.of("reason", "대상 플레이어를 찾을 수 없거나 금액이 올바르지 않습니다.")));
                return true;
            }
            economy.pay(player.getUniqueId(), target.getUniqueId(), amount).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (ok) {
                    player.sendMessage(messages.prefixed("money-paid", Map.of("target", target.getName(), "amount", String.valueOf(amount))));
                    target.sendMessage(messages.prefixed("money-received", Map.of("sender", player.getName(), "amount", String.valueOf(amount))));
                } else {
                    player.sendMessage(messages.prefixed("economy-failed", Map.of("reason", "잔액이 부족하거나 상대의 보유 한도를 초과했습니다.")));
                }
            }));
            return true;
        }
        if (args[0].equalsIgnoreCase("top")) {
            economy.topBalances(10).thenAccept(rows -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (rows.isEmpty()) {
                    sender.sendMessage("잔액 순위가 비어 있습니다.");
                    return;
                }
                StringBuilder out = new StringBuilder("<gold>Crown 순위</gold>");
                int rank = 1;
                for (EconomyService.BalanceRow row : rows) {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(row.playerUuid());
                    String name = offline.getName() == null ? row.playerUuid().toString() : offline.getName();
                    out.append('\n').append(rank++).append(". ").append(name).append(": ").append(row.balance()).append(" Crown");
                }
                sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(out.toString()));
            }));
            return true;
        }
        sender.sendMessage("사용법: /money 또는 /money pay <플레이어> <금액>");
        return true;
    }

    private Long parseAmount(Player player, String raw) {
        try {
            long amount = Long.parseLong(raw);
            if (amount <= 0) {
                player.sendMessage(messages.prefixed("economy-failed", Map.of("reason", "금액은 1 이상이어야 합니다.")));
                return null;
            }
            return amount;
        } catch (NumberFormatException e) {
            player.sendMessage(messages.prefixed("economy-failed", Map.of("reason", "금액은 숫자로 입력해야 합니다: " + raw)));
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("pay", "top").stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("pay")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        return List.of();
    }
}
