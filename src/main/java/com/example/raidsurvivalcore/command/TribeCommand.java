package com.example.raidsurvivalcore.command;

import com.example.raidsurvivalcore.chat.TribeChatState;
import com.example.raidsurvivalcore.combat.CombatManager;
import com.example.raidsurvivalcore.config.MessageService;
import com.example.raidsurvivalcore.tribe.Tribe;
import com.example.raidsurvivalcore.tribe.TribeHome;
import com.example.raidsurvivalcore.tribe.TribeMember;
import com.example.raidsurvivalcore.tribe.TribeRole;
import com.example.raidsurvivalcore.tribe.TribeService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class TribeCommand implements TabExecutor {
    private final JavaPlugin plugin;
    private final TribeService tribes;
    private final TribeChatState chatState;
    private final MessageService messages;
    private final CombatManager combat;

    public TribeCommand(JavaPlugin plugin, TribeService tribes, TribeChatState chatState, MessageService messages, CombatManager combat) {
        this.plugin = plugin;
        this.tribes = tribes;
        this.chatState = chatState;
        this.messages = messages;
        this.combat = combat;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (command.getName().equalsIgnoreCase("tc")) return tc(sender, args);
            if (!(sender instanceof Player player)) {
                sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
                return true;
            }
            if (args.length == 0) return info(player, null);
            switch (args[0].toLowerCase()) {
                case "create" -> {
                    if (args.length < 3) return usage(sender, "사용법: /tribe create <부족이름> <태그>");
                    tribes.create(player.getUniqueId(), args[1], args[2], true).thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (result.success()) player.sendMessage(messages.prefixed("tribe-created", Map.of("name", args[1], "tag", args[2])));
                        else player.sendMessage(messages.prefixed("tribe-condition-failed", Map.of("reason", koreanCreateReason(result.reason()))));
                    }));
                }
                case "invite" -> {
                    if (args.length < 2) return usage(sender, "사용법: /tribe invite <플레이어>");
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target == null) return usage(sender, "초대할 플레이어를 찾을 수 없습니다. 대상은 온라인이어야 합니다.");
                    tribes.invite(player.getUniqueId(), target.getUniqueId()).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!ok) {
                            player.sendMessage(messages.prefixed("tribe-condition-failed", Map.of("reason", "초대에 실패했습니다. 본인이 부족 간부 이상인지, 부족에 가입되어 있는지 확인하세요.")));
                            return;
                        }
                        player.sendMessage(messages.prefixed("tribe-invite-sent", Map.of("target", target.getName())));
                        tribes.snapshot().member(player.getUniqueId())
                            .flatMap(member -> tribes.snapshot().tribe(member.tribeId()))
                            .ifPresent(tribe -> sendInviteMessage(player, target, tribe.name()));
                    }));
                }
                case "accept" -> {
                    if (args.length < 2) return usage(sender, "사용법: /tribe accept <부족이름>");
                    tribes.accept(player.getUniqueId(), args[1]).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(ok ? messages.prefixed("tribe-joined", Map.of("tribe", args[1])) : messages.prefixed("tribe-condition-failed", Map.of("reason", "해당 부족의 초대가 없거나 초대가 만료되었습니다. 이미 다른 부족에 가입되어 있어도 가입할 수 없습니다.")))));
                }
                case "decline" -> sender.sendMessage("초대를 거절했습니다.");
                case "leave" -> tribes.leave(player.getUniqueId()).thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(leaveMessage(result))));
                case "disband" -> tribes.disband(player.getUniqueId()).thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(disbandMessage(result))));
                case "kick", "promote", "demote", "transfer" -> sender.sendMessage("부족 관리 명령은 아직 복구/관리 절차로 처리됩니다. 관리자 권한 raidcore.tribe.manage와 대상 기록을 확인하세요.");
                case "info" -> info(player, args.length > 1 ? args[1] : null);
                case "members" -> members(player, args.length > 1 ? args[1] : null);
                case "top" -> tribes.topSummary().thenAccept(player::sendMessage);
                case "contribution" -> sender.sendMessage("기여도는 이벤트별로 기록되며 관리자 감사 기록에서 확인할 수 있습니다.");
                case "treasury" -> sender.sendMessage("사용법: /tribe deposit <금액> 또는 /tribe withdraw <금액>");
                case "deposit" -> {
                    if (args.length < 2) return usage(sender, "사용법: /tribe deposit <금액>");
                    Long amount = parseAmount(sender, args[1]);
                    if (amount == null) return true;
                    tribes.deposit(player.getUniqueId(), amount).thenAccept(ok -> player.sendMessage(ok ? amount + " Crown을 부족 금고에 입금했습니다." : "입금에 실패했습니다. 부족 가입 여부, 보유 Crown, 금액이 올바른지 확인하세요."));
                }
                case "withdraw" -> {
                    if (args.length < 2) return usage(sender, "사용법: /tribe withdraw <금액>");
                    Long amount = parseAmount(sender, args[1]);
                    if (amount == null) return true;
                    tribes.withdraw(player.getUniqueId(), amount).thenAccept(ok -> player.sendMessage(ok ? amount + " Crown을 부족 금고에서 출금했습니다." : "출금에 실패했습니다. 부족장/간부 권한, 부족 금고 잔액, 금액이 올바른지 확인하세요."));
                }
                case "chat" -> {
                    boolean enabled = chatState.toggle(player.getUniqueId());
                    player.sendMessage(messages.prefixed(enabled ? "tribe-chat-enabled" : "tribe-chat-disabled", Map.of()));
                }
                case "sethome" -> setHome(player);
                case "home" -> home(player);
                case "tpa", "tp" -> {
                    if (args.length < 2) return usage(sender, "사용법: /tribe tp <부족원>");
                    tpa(player, args[1]);
                }
                case "summon", "tpall" -> summon(player);
                case "relation" -> sender.sendMessage("동맹 또는 전쟁 관계가 없으면 기본 관계는 중립입니다.");
                case "war" -> {
                    if (args.length >= 3 && args[1].equalsIgnoreCase("declare")) {
                        tribes.declareWar(player.getUniqueId(), args[2]).thenAccept(ok -> player.sendMessage(ok ? "전쟁 선포를 기록했습니다." : "전쟁 선포에 실패했습니다. 대상 부족 이름, 본인 부족 가입 여부, 중복 전쟁 상태를 확인하세요."));
                    } else if (args.length >= 2 && args[1].equalsIgnoreCase("status")) {
                        player.sendMessage("전쟁 상태는 tribe_wars 테이블에 영구 기록됩니다.");
                    } else usage(sender, "사용법: /tribe war declare <부족이름> 또는 /tribe war status");
                }
                case "core" -> sender.sendMessage("코어 명령어: /tribe core create, /tribe core info");
                case "territory" -> sender.sendMessage("영토 정보는 청크 인덱스에 저장된 부족 코어 스냅샷을 기준으로 계산됩니다.");
                case "admin" -> sender.sendMessage("관리자 복구는 /raidcore tribe 및 docs/ADMIN_RECOVERY.md 절차를 확인하세요.");
                default -> usage(sender, "사용법: /tribe create|invite|accept|leave|disband|info|members|top|chat|sethome|home|tp|tpall|core|war");
            }
            return true;
        } catch (RuntimeException e) {
            sender.sendMessage("부족 명령어 처리 중 오류가 발생했습니다: " + e.getMessage());
            sender.sendMessage("입력한 금액/플레이어/부족 이름을 확인한 뒤 다시 시도하세요.");
            return true;
        }
    }

    private void sendInviteMessage(Player inviter, Player target, String tribeName) {
        target.sendMessage(Component.text(inviter.getName() + "님이 " + tribeName + " 부족에 초대했습니다. ", NamedTextColor.GREEN)
            .append(Component.text("[수락]", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand("/tribe accept " + tribeName))
                .hoverEvent(HoverEvent.showText(Component.text("/tribe accept " + tribeName))))
            .append(Component.text(" 또는 /tribe accept " + tribeName, NamedTextColor.GRAY)));
    }

    private boolean tc(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("부족 채팅은 플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length == 0) return usage(sender, "사용법: /tc <메시지>");
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
            player.sendMessage("부족에 가입되어 있지 않습니다.");
            return true;
        }
        Tribe tribe = tribes.snapshot().tribe(member.tribeId()).orElse(null);
        player.sendMessage(tribe == null ? "부족 정보를 찾을 수 없습니다." : "부족 " + tribe.name() + " [" + tribe.tag() + "] 레벨=" + tribe.level() + " 경험치=" + tribe.totalExperience());
        return true;
    }

    private boolean members(Player player, String tribeName) {
        TribeMember member = tribes.snapshot().member(player.getUniqueId()).orElse(null);
        if (member == null) return usage(player, "부족에 가입되어 있지 않습니다.");
        List<String> names = new ArrayList<>();
        tribes.snapshot().membersByPlayer().values().stream().filter(m -> m.tribeId() == member.tribeId()).forEach(m -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(m.playerUuid());
            names.add((op.getName() == null ? m.playerUuid().toString() : op.getName()) + ":" + m.role());
        });
        player.sendMessage(String.join(", ", names));
        return true;
    }

    private boolean setHome(Player player) {
        TribeMember member = ownMember(player);
        if (member == null) return usage(player, "부족에 가입되어 있지 않습니다.");
        if (member.role() != TribeRole.OWNER) return usage(player, "부족장만 부족 홈을 설정할 수 있습니다.");
        Location location = player.getLocation();
        tribes.setHome(player.getUniqueId(), location.getWorld().getUID(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch())
            .thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(ok ? "부족 홈을 현재 위치로 설정했습니다." : "부족 홈 설정에 실패했습니다. 부족장 권한과 DB 상태를 확인하세요.")));
        return true;
    }

    private boolean home(Player player) {
        if (combat.isTagged(player.getUniqueId())) {
            player.sendMessage(messages.prefixed("combat-command-blocked", Map.of()));
            return true;
        }
        if (ownMember(player) == null) return usage(player, "부족에 가입되어 있지 않습니다.");
        tribes.home(player.getUniqueId()).thenAccept(home -> Bukkit.getScheduler().runTask(plugin, () -> teleportHome(player, home)));
        return true;
    }

    private void teleportHome(Player player, Optional<TribeHome> home) {
        if (!player.isOnline()) return;
        if (combat.isTagged(player.getUniqueId())) {
            player.sendMessage(messages.prefixed("combat-command-blocked", Map.of()));
            return;
        }
        if (home.isEmpty()) {
            player.sendMessage("부족 홈이 아직 설정되지 않았습니다. 부족장이 /tribe sethome을 먼저 사용해야 합니다.");
            return;
        }
        World world = Bukkit.getWorld(home.get().worldUuid());
        if (world == null) {
            player.sendMessage("부족 홈이 저장된 월드가 현재 서버에 로드되어 있지 않습니다.");
            return;
        }
        player.teleport(new Location(world, home.get().x(), home.get().y(), home.get().z(), home.get().yaw(), home.get().pitch()));
        player.sendMessage("부족 홈으로 이동했습니다.");
    }

    private boolean tpa(Player player, String targetName) {
        if (combat.isTagged(player.getUniqueId())) {
            player.sendMessage(messages.prefixed("combat-command-blocked", Map.of()));
            return true;
        }
        TribeMember member = ownMember(player);
        if (member == null) return usage(player, "부족에 가입되어 있지 않습니다.");
        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) return usage(player, "이동할 플레이어를 찾을 수 없습니다. 대상은 온라인이어야 합니다.");
        if (target.equals(player)) return usage(player, "자기 자신에게는 이동할 수 없습니다.");
        TribeMember targetMember = tribes.snapshot().member(target.getUniqueId()).orElse(null);
        if (targetMember == null || targetMember.tribeId() != member.tribeId()) return usage(player, "대상이 같은 부족원이 아닙니다.");
        if (combat.isTagged(target.getUniqueId())) {
            player.sendMessage("대상이 전투 중이라 이동할 수 없습니다.");
            return true;
        }
        player.teleportAsync(target.getLocation()).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            player.sendMessage(target.getName() + "님에게 이동했습니다.");
            target.sendMessage(player.getName() + "님이 부족 TP로 당신에게 이동했습니다.");
        }));
        return true;
    }

    private boolean summon(Player player) {
        if (combat.isTagged(player.getUniqueId())) {
            player.sendMessage(messages.prefixed("combat-command-blocked", Map.of()));
            return true;
        }
        TribeMember member = ownMember(player);
        if (member == null) return usage(player, "부족에 가입되어 있지 않습니다.");
        if (member.role() != TribeRole.OWNER) return usage(player, "부족장만 부족원을 소집할 수 있습니다.");
        int moved = 0;
        Location destination = player.getLocation();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player)) continue;
            TribeMember targetMember = tribes.snapshot().member(target.getUniqueId()).orElse(null);
            if (targetMember == null || targetMember.tribeId() != member.tribeId()) continue;
            if (combat.isTagged(target.getUniqueId())) {
                target.sendMessage("전투 중이라 부족장 소집에서 제외되었습니다.");
                continue;
            }
            target.teleportAsync(destination);
            target.sendMessage("부족장이 당신을 소집했습니다.");
            moved++;
        }
        player.sendMessage("온라인 부족원 " + moved + "명을 소집했습니다.");
        return true;
    }

    private Long parseAmount(CommandSender sender, String raw) {
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

    private String koreanCreateReason(String reason) {
        return switch (reason) {
            case "creation requirements" -> "부족 생성 조건을 만족하지 못했습니다.";
            case "invalid name" -> "부족 이름 또는 태그 형식이 올바르지 않습니다.";
            case "already in tribe" -> "이미 부족에 가입되어 있습니다.";
            case "duplicate name" -> "같은 이름의 부족이 이미 존재합니다.";
            case "insufficient balance" -> "Crown이 부족합니다. 부족 생성에는 5000 Crown이 필요합니다.";
            default -> reason == null || reason.isBlank() ? "알 수 없는 오류입니다. 콘솔 로그를 확인하세요." : reason;
        };
    }

    private net.kyori.adventure.text.Component leaveMessage(TribeService.LeaveResult result) {
        return switch (result) {
            case LEFT -> messages.prefixed("tribe-left", Map.of());
            case DISBANDED -> messages.prefixed("tribe-disbanded", Map.of());
            case OWNER_HAS_MEMBERS -> messages.prefixed("tribe-owner-leave-blocked", Map.of());
            case NOT_IN_TRIBE -> messages.prefixed("tribe-condition-failed", Map.of("reason", "부족에 가입되어 있지 않습니다."));
            case NOT_OWNER -> messages.prefixed("tribe-condition-failed", Map.of("reason", "부족장만 사용할 수 있습니다."));
            case FAILED -> messages.prefixed("tribe-condition-failed", Map.of("reason", "DB 처리 중 오류가 발생했습니다. 콘솔 로그를 확인하세요."));
        };
    }

    private net.kyori.adventure.text.Component disbandMessage(TribeService.LeaveResult result) {
        return result == TribeService.LeaveResult.DISBANDED ? messages.prefixed("tribe-disbanded", Map.of()) : leaveMessage(result);
    }

    private TribeMember ownMember(Player player) {
        return tribes.snapshot().member(player.getUniqueId()).orElse(null);
    }

    private boolean usage(CommandSender sender, String usage) {
        sender.sendMessage(usage);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("tc")) return List.of();
        if (args.length == 1) return filter(List.of("create", "invite", "accept", "decline", "leave", "disband", "kick", "promote", "demote", "transfer", "info", "members", "top", "contribution", "treasury", "deposit", "withdraw", "chat", "sethome", "home", "tp", "tpa", "tpall", "summon", "relation", "war", "core", "territory", "admin"), args[0]);
        if (args.length == 2 && List.of("invite", "kick", "promote", "demote", "transfer", "tpa", "tp").contains(args[0].toLowerCase())) return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("war")) return filter(List.of("declare", "status"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("core")) return filter(List.of("create", "info"), args[1]);
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).toList();
    }
}
