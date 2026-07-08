package com.example.raidsurvivalcore.economy;

import io.papermc.paper.advancement.AdvancementDisplay;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public final class EconomyIncomeManager implements Listener {
    private final Plugin plugin;
    private final EconomyService economy;
    private int taskId = -1;

    public EconomyIncomeManager(Plugin plugin, EconomyService economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public void start() {
        stop();
        long intervalTicks = Math.max(1L, economy.settings().playtimeIntervalMinutes()) * 60L * 20L;
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::payPlaytimeIncome, intervalTicks, intervalTicks).getTaskId();
    }

    public void stop() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long starting = economy.settings().newAccountStartingBalance();
        boolean firstJoin = !player.hasPlayedBefore();
        if (starting <= 0) return;
        economy.balance(player.getUniqueId()).thenRun(() -> {
            if (firstJoin) notifyReward(player, starting, "신규 유저 시작 보상");
        });
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || event.getEntity() instanceof Player) return;
        EconomySettings settings = economy.settings();
        long amount = random(settings.mobLowRollMin(), settings.mobLowRollMax());
        if (amount <= 0) return;
        economy.award(killer.getUniqueId(), amount, CurrencyReason.MOB_REWARD).thenAccept(ok -> {
            if (ok) notifyReward(killer, amount, "몹 처치 보상");
        });
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        long amount = advancementReward(event.getAdvancement());
        if (amount <= 0) return;
        economy.award(player.getUniqueId(), amount, CurrencyReason.ADVANCEMENT_REWARD).thenAccept(ok -> {
            if (ok) notifyReward(player, amount, "도전과제 달성 보상");
        });
    }

    private void payPlaytimeIncome() {
        EconomySettings settings = economy.settings();
        long hourly = random(settings.hourlyGeneralTargetMin(), settings.hourlyGeneralTargetMax());
        long amount = hourly <= 0 ? 0 : Math.max(1, Math.round(hourly * (settings.playtimeIntervalMinutes() / 60.0)));
        if (amount <= 0) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            economy.award(player.getUniqueId(), amount, CurrencyReason.PLAYTIME_REWARD).thenAccept(ok -> {
                if (ok) notifyReward(player, amount, "플레이 보상");
            });
        }
    }

    private long advancementReward(Advancement advancement) {
        AdvancementDisplay display = advancement.getDisplay();
        if (display == null) return 0;
        EconomySettings settings = economy.settings();
        return switch (display.frame()) {
            case TASK -> settings.advancementTaskReward();
            case GOAL -> settings.advancementGoalReward();
            case CHALLENGE -> settings.advancementChallengeReward();
        };
    }

    private void notifyReward(Player player, long amount, String reason) {
        if (amount <= 0 || !player.isOnline()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) player.sendMessage(reason + "으로 " + amount + " Crown을 획득했습니다.");
        });
    }

    private long random(long min, long max) {
        long low = Math.max(0, Math.min(min, max));
        long high = Math.max(0, Math.max(min, max));
        if (high <= 0) return 0;
        if (low == high) return low;
        return ThreadLocalRandom.current().nextLong(low, high + 1);
    }
}
