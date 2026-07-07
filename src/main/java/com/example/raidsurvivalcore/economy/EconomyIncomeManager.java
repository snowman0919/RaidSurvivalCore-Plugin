package com.example.raidsurvivalcore.economy;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
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
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::payPlaytimeIncome, 20L * 60L, 20L * 60L).getTaskId();
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
            if (firstJoin && player.isOnline()) Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("신규 유저 시작 보상으로 " + starting + " Crown을 받았습니다."));
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
            if (ok && killer.isOnline()) Bukkit.getScheduler().runTask(plugin, () -> killer.sendMessage("몹 처치 보상 +" + amount + " Crown"));
        });
    }

    private void payPlaytimeIncome() {
        EconomySettings settings = economy.settings();
        long hourly = random(settings.hourlyGeneralTargetMin(), settings.hourlyGeneralTargetMax());
        long amount = hourly <= 0 ? 0 : Math.max(1, hourly / 60L);
        if (amount <= 0) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            economy.award(player.getUniqueId(), amount, CurrencyReason.PLAYTIME_REWARD).thenAccept(ok -> {
                if (ok && player.isOnline()) Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("플레이 보상 +" + amount + " Crown"));
            });
        }
    }

    private long random(long min, long max) {
        long low = Math.max(0, Math.min(min, max));
        long high = Math.max(0, Math.max(min, max));
        if (high <= 0) return 0;
        if (low == high) return low;
        return ThreadLocalRandom.current().nextLong(low, high + 1);
    }
}
