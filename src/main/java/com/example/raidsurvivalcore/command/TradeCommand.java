package com.example.raidsurvivalcore.command;

import com.example.raidsurvivalcore.economy.EconomyService;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class TradeCommand implements TabExecutor {
    private static final Duration REQUEST_TTL = Duration.ofSeconds(60);
    private static final Duration SESSION_TTL = Duration.ofMinutes(5);

    private final JavaPlugin plugin;
    private final EconomyService economy;
    private final Map<UUID, Request> requestsByTarget = new HashMap<>();
    private final Map<Pair, Instant> sessions = new HashMap<>();

    public TradeCommand(JavaPlugin plugin, EconomyService economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("거래는 플레이어만 사용할 수 있습니다.");
            return true;
        }
        cleanup();
        if (args.length < 1) return usage(sender);
        return switch (args[0].toLowerCase()) {
            case "request" -> request(player, args);
            case "accept" -> accept(player, args);
            case "item" -> tradeItem(player, args);
            case "money" -> tradeMoney(player, args);
            case "cancel" -> cancel(player, args);
            default -> usage(sender);
        };
    }

    private boolean request(Player sender, String[] args) {
        if (args.length < 2) return usage(sender);
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline() || target.equals(sender)) {
            sender.sendMessage("거래 요청 대상을 찾을 수 없습니다.");
            return true;
        }
        requestsByTarget.put(target.getUniqueId(), new Request(sender.getUniqueId(), Instant.now().plus(REQUEST_TTL)));
        sender.sendMessage(target.getName() + "님에게 거래 요청을 보냈습니다.");
        target.sendMessage(sender.getName() + "님이 거래를 요청했습니다. /trade accept " + sender.getName());
        return true;
    }

    private boolean accept(Player target, String[] args) {
        if (args.length < 2) return usage(target);
        Player requester = Bukkit.getPlayer(args[1]);
        Request request = requestsByTarget.get(target.getUniqueId());
        if (requester == null || request == null || !request.requester().equals(requester.getUniqueId()) || request.expiresAt().isBefore(Instant.now())) {
            target.sendMessage("유효한 거래 요청이 없습니다.");
            return true;
        }
        requestsByTarget.remove(target.getUniqueId());
        sessions.put(Pair.of(target.getUniqueId(), requester.getUniqueId()), Instant.now().plus(SESSION_TTL));
        target.sendMessage(requester.getName() + "님과 거래가 열렸습니다. /trade item 또는 /trade money로 직접 전송하세요.");
        requester.sendMessage(target.getName() + "님과 거래가 열렸습니다. /trade item 또는 /trade money로 직접 전송하세요.");
        return true;
    }

    private boolean tradeItem(Player sender, String[] args) {
        if (args.length < 3) return usage(sender);
        Player target = Bukkit.getPlayer(args[1]);
        Integer amount = parsePositive(sender, args[2], "수량");
        if (target == null || amount == null || !active(sender, target)) return true;
        ItemStack hand = sender.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || hand.getAmount() < amount) {
            sender.sendMessage("손에 든 아이템 수량이 부족합니다.");
            return true;
        }
        ItemStack moved = hand.clone();
        moved.setAmount(amount);
        hand.setAmount(hand.getAmount() - amount);
        sender.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        target.getInventory().addItem(moved).values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        sender.sendMessage(target.getName() + "님에게 " + moved.getType() + " x" + amount + "을 보냈습니다.");
        target.sendMessage(sender.getName() + "님에게서 " + moved.getType() + " x" + amount + "을 받았습니다.");
        return true;
    }

    private boolean tradeMoney(Player sender, String[] args) {
        if (args.length < 3) return usage(sender);
        Player target = Bukkit.getPlayer(args[1]);
        Long amount = parsePositiveLong(sender, args[2], "금액");
        if (target == null || amount == null || !active(sender, target)) return true;
        economy.pay(sender.getUniqueId(), target.getUniqueId(), amount).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!ok) {
                sender.sendMessage("송금 실패: 잔액 부족, 보유 한도 초과, 또는 DB 오류입니다.");
                return;
            }
            sender.sendMessage(target.getName() + "님에게 " + amount + " Crown을 보냈습니다.");
            target.sendMessage(sender.getName() + "님에게서 " + amount + " Crown을 받았습니다.");
        }));
        return true;
    }

    private boolean cancel(Player sender, String[] args) {
        if (args.length < 2) return usage(sender);
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("대상을 찾을 수 없습니다.");
            return true;
        }
        sessions.remove(Pair.of(sender.getUniqueId(), target.getUniqueId()));
        requestsByTarget.remove(sender.getUniqueId());
        requestsByTarget.remove(target.getUniqueId());
        sender.sendMessage("거래 요청 또는 세션을 취소했습니다.");
        target.sendMessage(sender.getName() + "님이 거래를 취소했습니다.");
        return true;
    }

    private boolean active(Player sender, Player target) {
        if (target == null || !target.isOnline() || target.equals(sender)) {
            sender.sendMessage("거래 대상을 찾을 수 없습니다.");
            return false;
        }
        Instant expires = sessions.get(Pair.of(sender.getUniqueId(), target.getUniqueId()));
        if (expires == null || !expires.isAfter(Instant.now())) {
            sender.sendMessage("먼저 /trade request 와 /trade accept 로 거래를 여세요.");
            return false;
        }
        return true;
    }

    private void cleanup() {
        Instant now = Instant.now();
        requestsByTarget.entrySet().removeIf(e -> !e.getValue().expiresAt().isAfter(now));
        sessions.entrySet().removeIf(e -> !e.getValue().isAfter(now));
    }

    private Integer parsePositive(Player sender, String raw, String label) {
        Long value = parsePositiveLong(sender, raw, label);
        if (value == null) return null;
        if (value > 64) {
            sender.sendMessage(label + "은 64 이하로 입력하세요.");
            return null;
        }
        return value.intValue();
    }

    private Long parsePositiveLong(Player sender, String raw, String label) {
        try {
            long value = Long.parseLong(raw);
            if (value <= 0) {
                sender.sendMessage(label + "은 1 이상이어야 합니다.");
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            sender.sendMessage(label + "은 숫자로 입력하세요: " + raw);
            return null;
        }
    }

    private boolean usage(CommandSender sender) {
        sender.sendMessage("사용법: /trade request <플레이어> | /trade accept <플레이어> | /trade item <플레이어> <수량> | /trade money <플레이어> <금액> | /trade cancel <플레이어>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("request", "accept", "item", "money", "cancel"), args[0]);
        if (args.length == 2) return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(option -> option.toLowerCase().startsWith(lower)).toList();
    }

    private record Request(UUID requester, Instant expiresAt) {
    }

    private record Pair(UUID first, UUID second) {
        static Pair of(UUID a, UUID b) {
            return a.compareTo(b) <= 0 ? new Pair(a, b) : new Pair(b, a);
        }
    }
}
