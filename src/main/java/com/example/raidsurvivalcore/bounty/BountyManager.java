package com.example.raidsurvivalcore.bounty;

import com.example.raidsurvivalcore.config.MessageService;
import com.example.raidsurvivalcore.config.RaidCoreConfig;
import com.example.raidsurvivalcore.economy.CurrencyReason;
import com.example.raidsurvivalcore.economy.EconomyRules;
import com.example.raidsurvivalcore.economy.EconomyService;
import com.example.raidsurvivalcore.persistence.DatabaseManager;
import com.example.raidsurvivalcore.util.ItemStackCodec;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class BountyManager {
    private final DatabaseManager database;
    private final EconomyService economy;
    private final MessageService messages;
    private final Logger logger;
    private RaidCoreConfig config;

    public BountyManager(DatabaseManager database, EconomyService economy, MessageService messages, Logger logger, RaidCoreConfig config) {
        this.database = database;
        this.economy = economy;
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
        CompletableFuture.supplyAsync(() -> {
            try (var c = database.connection()) {
                c.setAutoCommit(false);
                String account = economy.personalAccount(setter.getUniqueId());
                economy.ensureAccount(c, account, "PLAYER");
                long balance = economy.balanceSync(c, account);
                economy.setBalance(c, account, EconomyRules.checkedSubtract(balance, clamped));
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO bounties(target_uuid, amount) VALUES(?, ?) ON CONFLICT(target_uuid) DO UPDATE SET amount = amount + excluded.amount")) {
                    ps.setString(1, target.getUniqueId().toString());
                    ps.setLong(2, clamped);
                    ps.executeUpdate();
                }
                economy.insertTx(c, UUID.randomUUID().toString(), setter.getUniqueId().toString(), account, "bounty:" + target.getUniqueId(), clamped, CurrencyReason.BOUNTY_CREATE.name(), 0);
                c.commit();
                return true;
            } catch (Exception e) {
                logger.warning("Failed to add bounty: " + e.getMessage());
                return false;
            }
        }, database.executor()).thenAccept(ok -> runMain(() -> setter.sendMessage(ok
            ? messages.prefixed("bounty-set", Map.of("target", target.getName() == null ? target.getUniqueId().toString() : target.getName(), "amount", String.valueOf(clamped)))
            : messages.prefixed("economy-failed", Map.of("reason", "현상금 등록 실패: Crown 부족 또는 DB 오류입니다."))
        )));
    }

    public void addItemBounty(Player setter, OfflinePlayer target, int amount) {
        if (!config.bounty().enabled()) return;
        if (setter.getUniqueId().equals(target.getUniqueId())) return;
        ItemStack hand = setter.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || hand.getAmount() < amount || amount <= 0) {
            setter.sendMessage("현상금으로 걸 아이템을 손에 들고 올바른 수량을 입력하세요.");
            return;
        }
        ItemStack reward = hand.clone();
        reward.setAmount(amount);
        hand.setAmount(hand.getAmount() - amount);
        setter.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        CompletableFuture.supplyAsync(() -> {
            try (var c = database.connection();
                 PreparedStatement ps = c.prepareStatement("INSERT INTO bounty_items(target_uuid,item_blob,created_at) VALUES(?,?,?)")) {
                ps.setString(1, target.getUniqueId().toString());
                ps.setString(2, ItemStackCodec.encode(reward));
                ps.setLong(3, Instant.now().toEpochMilli());
                ps.executeUpdate();
                return true;
            } catch (Exception e) {
                logger.warning("Failed to add item bounty: " + e.getMessage());
                return false;
            }
        }, database.executor()).thenAccept(ok -> runMain(() -> {
            if (ok) {
                setter.sendMessage((target.getName() == null ? target.getUniqueId().toString() : target.getName()) + "에게 아이템 현상금을 걸었습니다: " + reward.getType() + " x" + reward.getAmount());
                return;
            }
            setter.getInventory().addItem(reward).values().forEach(left -> setter.getWorld().dropItemNaturally(setter.getLocation(), left));
            setter.sendMessage("아이템 현상금 등록 실패: 아이템을 되돌렸습니다.");
        }));
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
            List<ItemStack> items = new ArrayList<>();
            if (multiplier > 0) {
                try (PreparedStatement ps = c.prepareStatement("SELECT item_blob FROM bounty_items WHERE target_uuid=?")) {
                    ps.setString(1, victim.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            try {
                                items.add(ItemStackCodec.decode(rs.getString(1)));
                            } catch (Exception e) {
                                logger.warning("Failed to decode item bounty: " + e.getMessage());
                            }
                        }
                    }
                }
            }
            if (amount > 0 && multiplier > 0) {
                long paid = Math.round(amount * multiplier);
                String account = economy.personalAccount(killer);
                economy.ensureAccount(c, account, "PLAYER");
                long balance = economy.balanceSync(c, account);
                economy.setBalance(c, account, EconomyRules.checkedAdd(balance, paid, economy.settings().maxPersonalBalance()));
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM bounties WHERE target_uuid=?")) {
                    ps.setString(1, victim.toString());
                    ps.executeUpdate();
                }
                economy.insertTx(c, UUID.randomUUID().toString(), killer.toString(), "bounty:" + victim, account, paid, CurrencyReason.BOUNTY_REWARD.name(), 0);
                Player online = Bukkit.getPlayer(killer);
                if (online != null) runMain(() -> online.sendMessage(messages.prefixed("bounty-paid", Map.of("target", Bukkit.getOfflinePlayer(victim).getName() == null ? victim.toString() : Bukkit.getOfflinePlayer(victim).getName(), "amount", String.valueOf(paid)))));
            }
            if (!items.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM bounty_items WHERE target_uuid=?")) {
                    ps.setString(1, victim.toString());
                    ps.executeUpdate();
                }
                runMain(() -> payItems(killer, victim, items));
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO kill_records(killer_uuid,victim_uuid,killed_at) VALUES(?,?,?)")) {
                ps.setString(1, killer.toString());
                ps.setString(2, victim.toString());
                ps.setLong(3, Instant.now().toEpochMilli());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            logger.warning("Failed to pay bounty: " + e.getMessage());
        }
    }

    public CompletableFuture<List<String>> listTop() {
        return CompletableFuture.supplyAsync(() -> {
            List<String> rows = new ArrayList<>();
            try (var c = database.connection(); PreparedStatement ps = c.prepareStatement("""
                SELECT target_uuid, SUM(amount) AS money, 0 AS item_count FROM bounties GROUP BY target_uuid
                UNION ALL
                SELECT target_uuid, 0 AS money, COUNT(*) AS item_count FROM bounty_items GROUP BY target_uuid
                """); ResultSet rs = ps.executeQuery()) {
                Map<String, Summary> summaries = new java.util.HashMap<>();
                while (rs.next()) {
                    summaries.computeIfAbsent(rs.getString("target_uuid"), ignored -> new Summary()).add(rs.getLong("money"), rs.getInt("item_count"));
                }
                summaries.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue().money, a.getValue().money))
                    .limit(10)
                    .forEach(e -> rows.add(e.getKey() + ": " + e.getValue().money + " Crown, 아이템 " + e.getValue().items + "개"));
            } catch (SQLException e) {
                logger.warning("Failed to list bounties: " + e.getMessage());
            }
            return rows;
        }, database.executor());
    }

    public CompletableFuture<String> info(UUID target) {
        return CompletableFuture.supplyAsync(() -> {
            long money = 0;
            List<String> items = new ArrayList<>();
            try (var c = database.connection()) {
                try (PreparedStatement ps = c.prepareStatement("SELECT amount FROM bounties WHERE target_uuid=?")) {
                    ps.setString(1, target.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        money = rs.next() ? rs.getLong(1) : 0;
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("SELECT item_blob FROM bounty_items WHERE target_uuid=?")) {
                    ps.setString(1, target.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            try {
                                ItemStack item = ItemStackCodec.decode(rs.getString(1));
                                items.add(item.getType() + " x" + item.getAmount());
                            } catch (Exception e) {
                                logger.warning("Failed to decode item bounty info: " + e.getMessage());
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                logger.warning("Failed to read bounty info: " + e.getMessage());
            }
            if (money <= 0 && items.isEmpty()) return "등록된 현상금이 없습니다.";
            return "현상금: " + money + " Crown" + (items.isEmpty() ? "" : "\n아이템: " + String.join(", ", items));
        }, database.executor());
    }

    public void clear(UUID target) {
        CompletableFuture.runAsync(() -> {
            try (var c = database.connection()) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM bounties WHERE target_uuid=?")) {
                    ps.setString(1, target.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM bounty_items WHERE target_uuid=?")) {
                    ps.setString(1, target.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                logger.warning("Failed to clear bounty: " + e.getMessage());
            }
        }, database.executor());
    }

    private void payItems(UUID killer, UUID victim, List<ItemStack> items) {
        Player player = Bukkit.getPlayer(killer);
        if (player == null) return;
        for (ItemStack item : items) {
            player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        String targetName = Bukkit.getOfflinePlayer(victim).getName() == null ? victim.toString() : Bukkit.getOfflinePlayer(victim).getName();
        player.sendMessage(targetName + "의 아이템 현상금 " + items.size() + "개를 획득했습니다.");
    }

    private void runMain(Runnable runnable) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("RaidSurvivalCore");
        if (plugin == null || !plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private static final class Summary {
        private long money;
        private int items;

        private void add(long money, int items) {
            this.money += money;
            this.items += items;
        }
    }
}
