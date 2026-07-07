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
            sender.sendMessage("상점 기능은 economy.yml 설정과 서버 상점 연동 API를 사용합니다. 현재 빌드는 경제 저장소와 거래 API를 제공합니다.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length == 0) {
            economy.balance(player.getUniqueId()).thenAccept(balance -> player.sendMessage(messages.prefixed("money-balance", Map.of("amount", String.valueOf(balance)))));
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
            economy.pay(player.getUniqueId(), target.getUniqueId(), amount).thenAccept(ok -> Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("RaidSurvivalCore"), () -> {
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
            sender.sendMessage("잔액 순위는 개인 잔액 노출 위험이 있어 게임 내 공개 명령으로 제공하지 않습니다. 관리자 DB 도구에서 currency_accounts를 조회하세요.");
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
