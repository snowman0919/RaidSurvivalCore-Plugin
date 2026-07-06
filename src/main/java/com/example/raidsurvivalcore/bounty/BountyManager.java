package com.example.raidsurvivalcore.bounty;

import com.example.raidsurvivalcore.config.MessageService;
import com.example.raidsurvivalcore.config.RaidCoreConfig;
import com.example.raidsurvivalcore.persistence.DatabaseManager;
import com.example.raidsurvivalcore.persistence.PlayerDataRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class BountyManager {
    private final DatabaseManager database;
    private final PlayerDataRepository playerData;
    private final MessageService messages;
    private final Logger logger;
    private RaidCoreConfig config;

    public BountyManager(DatabaseManager database, PlayerDataRepository playerData, MessageService messages, Logger logger, RaidCoreConfig config) {
        this.database = database;
        this.playerData = playerData;
        this.messages = messages;
        this.logger = logger;
        this.config = config;
    }

    public void reload(RaidCoreConfig config) {
        this.config = config;
    }

    public void addBounty(Player setter, OfflinePlayer target, long amount) {
        if (!config.bounty().enabled()) return;
        if (setter.getUniqueId().equals(target.getUniqueId())) return;
        long clamped = Math.max(config.bounty().minAmount(), Math.min(config.bounty().maxAmount(), amount));
        CompletableFuture.runAsync(() -> {
            try (var c = database.connection();
                 PreparedStatement ps = c.prepareStatement("INSERT INTO bounties(target_uuid, amount) VALUES(?, ?) ON CONFLICT(target_uuid) DO UPDATE SET amount = amount + excluded.amount")) {
                ps.setString(1, target.getUniqueId().toString());
                ps.setLong(2, clamped);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.warning("Failed to add bounty: " + e.getMessage());
            }
        }, database.executor()).thenRun(() -> Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("RaidSurvivalCore"), () -> setter.sendMessage(messages.prefixed("bounty-set", Map.of("target", target.getName() == null ? target.getUniqueId().toString() : target.getName(), "amount", String.valueOf(clamped))))));
    }

    public void handleKill(UUID killer, UUID victim) {
        if (!config.bounty().enabled() || killer == null || victim == null || killer.equals(victim)) return;
        CompletableFuture.runAsync(() -> pay(killer, victim), database.executor());
    }

    private void pay(UUID killer, UUID victim) {
        try (var c = database.connection()) {
            long recentKills;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM kill_records WHERE killer_uuid=? AND victim_uuid=? AND killed_at>=?")) {
                ps.setString(1, killer.toString());
                ps.setString(2, victim.toString());
                ps.setLong(3, Instant.now().minus(config.antiFarming().window()).toEpochMilli());
                try (ResultSet rs = ps.executeQuery()) {
                    recentKills = rs.next() ? rs.getLong(1) : 0;
                }
            }
            double multiplier = config.antiFarming().enabled() ? AntiFarmingRules.rewardMultiplier((int) recentKills, config.antiFarming().fullRewardKills(), config.antiFarming().noRewardFromKill(), config.antiFarming().reducedMultiplier()) : 1.0;
            long amount;
            try (PreparedStatement ps = c.prepareStatement("SELECT amount FROM bounties WHERE target_uuid=?")) {
                ps.setString(1, victim.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    amount = rs.next() ? rs.getLong(1) : 0;
                }
            }
            if (amount > 0 && multiplier > 0) {
                long paid = Math.round(amount * multiplier);
                playerData.getOrCreate(killer).addTokens(paid);
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM bounties WHERE target_uuid=?")) {
                    ps.setString(1, victim.toString());
                    ps.executeUpdate();
                }
                Player online = Bukkit.getPlayer(killer);
                if (online != null) Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("RaidSurvivalCore"), () -> online.sendMessage(messages.prefixed("bounty-paid", Map.of("target", Bukkit.getOfflinePlayer(victim).getName() == null ? victim.toString() : Bukkit.getOfflinePlayer(victim).getName(), "amount", String.valueOf(paid)))));
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO kill_records(killer_uuid,victim_uuid,killed_at) VALUES(?,?,?)")) {
                ps.setString(1, killer.toString());
                ps.setString(2, victim.toString());
                ps.setLong(3, Instant.now().toEpochMilli());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.warning("Failed to pay bounty: " + e.getMessage());
        }
    }

    public CompletableFuture<List<String>> listTop() {
        return CompletableFuture.supplyAsync(() -> {
            List<String> rows = new ArrayList<>();
            try (var c = database.connection(); PreparedStatement ps = c.prepareStatement("SELECT target_uuid, amount FROM bounties ORDER BY amount DESC LIMIT 10"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(rs.getString(1) + ": " + rs.getLong(2));
            } catch (SQLException e) {
                logger.warning("Failed to list bounties: " + e.getMessage());
            }
            return rows;
        }, database.executor());
    }

    public void clear(UUID target) {
        CompletableFuture.runAsync(() -> {
            try (var c = database.connection(); PreparedStatement ps = c.prepareStatement("DELETE FROM bounties WHERE target_uuid=?")) {
                ps.setString(1, target.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.warning("Failed to clear bounty: " + e.getMessage());
            }
        }, database.executor());
    }
}
