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
        try {
            if (command.getName().equalsIgnoreCase("bounty")) return bountyCommand(sender, args);
            if (args.length == 0) {
                sender.sendMessage("사용법: /raidcore reload|status|combat|proximity|spawn|bounty|protection|debug|economy");
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
                    if (target == null) return usage(sender, "사용법: /raidcore status <플레이어>");
                    sender.sendMessage("전투상태=" + combat.isTagged(target.getUniqueId()) + ", 신규보호=" + newbie.protectedNow(target) + ", 근접정보=" + proximity.debug(target));
                }
                case "combat" -> combatSub(sender, args);
                case "proximity" -> {
                    Player target = args.length > 2 ? Bukkit.getPlayer(args[2]) : null;
                    if (target == null) return usage(sender, "사용법: /raidcore proximity clear <플레이어>");
                    proximity.clear(target);
                    sender.sendMessage("근접 페널티 상태를 초기화했습니다.");
                }
                case "spawn" -> {
                    if (args.length >= 2 && args[1].equalsIgnoreCase("test") && sender instanceof Player p) randomSpawn.randomTeleport(p);
                    else {
                        Player target = args.length > 2 ? Bukkit.getPlayer(args[2]) : null;
                        if (target == null) return usage(sender, "사용법: /raidcore spawn random <플레이어>");
                        randomSpawn.randomTeleport(target);
                    }
                }
                case "bounty" -> {
                    if (args.length > 2 && args[1].equalsIgnoreCase("clear")) {
                        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
                        bounty.clear(target.getUniqueId());
                        sender.sendMessage("현상금을 초기화했습니다.");
                    }
                }
                case "protection" -> {
                    if (args.length > 2 && args[1].equalsIgnoreCase("clear")) {
                        Player target = Bukkit.getPlayer(args[2]);
                        if (target == null) return usage(sender, "사용법: /raidcore protection clear <플레이어>");
                        newbie.disable(target);
                        sender.sendMessage("보호 상태를 해제했습니다.");
                    }
                }
                case "debug" -> {
                    Player target = args.length > 2 ? Bukkit.getPlayer(args[2]) : null;
                    if (target != null) sender.sendMessage(proximity.debug(target));
                    else sender.sendMessage("대상을 찾을 수 없습니다.");
                }
                case "economy" -> economySub(sender, args);
                case "tribe" -> sender.sendMessage("부족 관리자 복구는 SQLite 감사 테이블을 사용합니다. docs/ADMIN_RECOVERY.md를 확인하세요.");
                default -> sender.sendMessage("사용법: /raidcore reload|status|combat|proximity|spawn|bounty|protection|debug|economy");
            }
            return true;
        } catch (RuntimeException e) {
            sender.sendMessage("명령어 처리 중 오류가 발생했습니다: " + e.getMessage());
            return true;
        }
    }

    private void economySub(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raidcore.economy.admin")) {
            noPerm(sender);
            return;
        }
        if (args.length < 3) {
            usage(sender, "사용법: /raidcore economy balance|add|remove|set <플레이어> [금액] [사유]");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (args[1].equalsIgnoreCase("balance")) {
            economy.balance(target.getUniqueId()).thenAccept(balance -> sender.sendMessage(target.getName() + ": " + balance + " Crown"));
            return;
        }
        if (args.length < 5) {
            usage(sender, "사용법: /raidcore economy add|remove|set <플레이어> <금액> <사유>");
            return;
        }
        Long amount = parsePositiveLong(sender, args[3]);
        if (amount == null) return;
        CurrencyReason reason = parseReason(sender, args[4]);
        if (reason == null) return;
        java.util.UUID actor = sender instanceof Player player ? player.getUniqueId() : null;
        String mode = args[1].toLowerCase();
        if (!List.of("add", "remove", "set").contains(mode)) {
            usage(sender, "사용법: /raidcore economy add|remove|set <플레이어> <금액> <사유>");
            return;
        }
        economy.adminAdjust(actor, target.getUniqueId(), amount, reason, mode).thenAccept(ok -> {
            sender.sendMessage(ok ? "경제 정보를 수정했습니다." : "경제 정보 수정 실패: 잔액 부족, 한도 초과, 또는 내부 DB 오류입니다.");
            Player online = Bukkit.getPlayer(target.getUniqueId());
            if (ok && mode.equals("add") && online != null) {
                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("RaidSurvivalCore"), () -> online.sendMessage("관리자 지급으로 " + amount + " Crown을 획득했습니다."));
            }
        });
    }

    private boolean bountyCommand(CommandSender sender, String[] args) {
        if (args.length == 0) return usage(sender, "사용법: /bounty set <플레이어> <금액> 또는 /bounty list");
        if (args[0].equalsIgnoreCase("set") && !(sender instanceof Player)) {
            sender.sendMessage("현상금 등록은 플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args[0].equalsIgnoreCase("set") && sender instanceof Player player && args.length >= 3) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (target.getUniqueId().equals(player.getUniqueId())) {
                sender.sendMessage("자기 자신에게는 현상금을 걸 수 없습니다.");
                return true;
            }
            Long amount = parsePositiveLong(sender, args[2]);
            if (amount == null) return true;
            bounty.addBounty(player, target, amount);
            return true;
        }
        if (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("top")) {
            bounty.listTop().thenAccept(rows -> sender.sendMessage(String.join("\n", rows.isEmpty() ? List.of("등록된 현상금이 없습니다.") : rows)));
            return true;
        }
        if (args[0].equalsIgnoreCase("info") && args.length >= 2) {
            sender.sendMessage("저장된 현상금 목록은 /bounty top으로 확인하세요.");
            return true;
        }
        return usage(sender, "사용법: /bounty set <플레이어> <금액> 또는 /bounty list|top|info <플레이어>");
    }

    private void combatSub(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, "사용법: /raidcore combat clear|set <플레이어> [초]");
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage("플레이어를 찾을 수 없습니다.");
            return;
        }
        if (args[1].equalsIgnoreCase("clear")) combat.clear(target.getUniqueId());
        if (args[1].equalsIgnoreCase("set")) {
            Integer seconds = args.length > 3 ? parsePositiveInt(sender, args[3]) : 20;
            if (seconds == null) return;
            combat.set(target.getUniqueId(), seconds);
        }
        sender.sendMessage("전투 상태를 수정했습니다.");
    }

    private Long parsePositiveLong(CommandSender sender, String raw) {
        try {
            long amount = Long.parseLong(raw);
            if (amount <= 0) {
                sender.sendMessage("금액은 1 이상이어야 합니다.");
                return null;
            }
            return amount;
        } catch (NumberFormatException e) {
            sender.sendMessage("금액은 숫자로 입력해야 합니다: " + raw);
            return null;
        }
    }

    private Integer parsePositiveInt(CommandSender sender, String raw) {
        try {
            int amount = Integer.parseInt(raw);
            if (amount <= 0) {
                sender.sendMessage("초 값은 1 이상이어야 합니다.");
                return null;
            }
            return amount;
        } catch (NumberFormatException e) {
            sender.sendMessage("초 값은 숫자로 입력해야 합니다: " + raw);
            return null;
        }
    }

    private CurrencyReason parseReason(CommandSender sender, String raw) {
        String normalized = raw.toUpperCase();
        if (normalized.equals("ADMIN_ADJUST")) normalized = "ADMIN_ADJUSTMENT";
        try {
            return CurrencyReason.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("알 수 없는 경제 사유입니다: " + raw + " (예: ADMIN_ADJUSTMENT 또는 ADMIN_ADJUST)");
            return null;
        }
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
