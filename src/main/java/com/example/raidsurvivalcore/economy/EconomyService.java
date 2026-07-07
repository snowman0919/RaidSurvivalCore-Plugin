package com.example.raidsurvivalcore.economy;

import com.example.raidsurvivalcore.persistence.DatabaseManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public final class EconomyService {
    private final DatabaseManager database;
    private final Logger logger;
    private volatile EconomySettings settings;

    public EconomyService(DatabaseManager database, Logger logger, EconomySettings settings) {
        this.database = database;
        this.logger = logger;
        this.settings = settings;
    }

    public void reload(EconomySettings settings) {
        this.settings = settings;
    }

    public EconomySettings settings() {
        return settings;
    }

    public String personalAccount(UUID uuid) {
        return "player:" + uuid;
    }

    public CompletableFuture<Long> balance(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> balanceSync(personalAccount(uuid)), database.executor());
    }

    public CompletableFuture<Boolean> pay(UUID from, UUID to, long amount) {
        return CompletableFuture.supplyAsync(() -> transfer(personalAccount(from), personalAccount(to), from, amount, CurrencyReason.PLAYER_TRANSFER, settings.payTaxRate()), database.executor());
    }

    public CompletableFuture<Boolean> adminAdjust(UUID actor, UUID target, long amount, CurrencyReason reason, String mode) {
        return CompletableFuture.supplyAsync(() -> {
            try (var c = database.connection()) {
                c.setAutoCommit(false);
                ensureAccount(c, personalAccount(target), "PLAYER");
                long current = balanceSync(c, personalAccount(target));
                long next = switch (mode) {
                    case "add" -> EconomyRules.checkedAdd(current, amount, settings.maxPersonalBalance());
                    case "remove" -> EconomyRules.checkedSubtract(current, amount);
                    case "set" -> amount;
                    default -> throw new IllegalArgumentException("unknown mode");
                };
                setBalance(c, personalAccount(target), next);
                insertTx(c, UUID.randomUUID().toString(), actor == null ? null : actor.toString(), null, personalAccount(target), amount, reason.name(), 0);
                c.commit();
                return true;
            } catch (Exception e) {
                logger.warning("RaidSurvivalCore economy admin adjustment failed: " + e.getMessage());
                return false;
            }
        }, database.executor());
    }

    public CompletableFuture<Boolean> award(UUID target, long amount, CurrencyReason reason) {
        return CompletableFuture.supplyAsync(() -> {
            if (amount <= 0) return false;
            try (var c = database.connection()) {
                c.setAutoCommit(false);
                String account = personalAccount(target);
                ensureAccount(c, account, "PLAYER");
                long current = balanceSync(c, account);
                setBalance(c, account, EconomyRules.checkedAdd(current, amount, settings.maxPersonalBalance()));
                insertTx(c, UUID.randomUUID().toString(), null, null, account, amount, reason.name(), 0);
                c.commit();
                return true;
            } catch (Exception e) {
                logger.warning("RaidSurvivalCore economy reward failed: " + e.getMessage());
                return false;
            }
        }, database.executor());
    }

    public boolean transfer(String source, String target, UUID actor, long amount, CurrencyReason reason, double taxRate) {
        try (var c = database.connection()) {
            c.setAutoCommit(false);
            ensureAccount(c, source, "PLAYER");
            ensureAccount(c, target, "PLAYER");
            long sourceBalance = balanceSync(c, source);
            long tax = EconomyRules.tax(amount, taxRate);
            long debit = EconomyRules.checkedAdd(amount, tax, Long.MAX_VALUE);
            long targetBalance = balanceSync(c, target);
            setBalance(c, source, EconomyRules.checkedSubtract(sourceBalance, debit));
            setBalance(c, target, EconomyRules.checkedAdd(targetBalance, amount, settings.maxPersonalBalance()));
            insertTx(c, UUID.randomUUID().toString(), actor == null ? null : actor.toString(), source, target, amount, reason.name(), tax);
            c.commit();
            return true;
        } catch (Exception e) {
            logger.warning("RaidSurvivalCore economy transfer failed: " + e.getMessage());
            return false;
        }
    }

    public long balanceSync(String accountId) {
        try (var c = database.connection()) {
            ensureAccount(c, accountId, accountId.startsWith("tribe:") ? "TRIBE" : "PLAYER");
            return balanceSync(c, accountId);
        } catch (SQLException e) {
            logger.warning("RaidSurvivalCore economy balance read failed: " + e.getMessage());
            return 0;
        }
    }

    public void ensureAccount(java.sql.Connection c, String accountId, String type) throws SQLException {
        long startingBalance = "PLAYER".equals(type) ? settings.newAccountStartingBalance() : 0;
        try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO currency_accounts(account_id, account_type, balance, updated_at) VALUES(?,?,?,?)")) {
            ps.setString(1, accountId);
            ps.setString(2, type);
            ps.setLong(3, startingBalance);
            ps.setLong(4, Instant.now().toEpochMilli());
            ps.executeUpdate();
        }
    }

    public long balanceSync(java.sql.Connection c, String accountId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT balance FROM currency_accounts WHERE account_id=?")) {
            ps.setString(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    public void setBalance(java.sql.Connection c, String accountId, long amount) throws SQLException {
        if (amount < 0) throw new IllegalArgumentException("negative balance");
        try (PreparedStatement ps = c.prepareStatement("UPDATE currency_accounts SET balance=?, updated_at=? WHERE account_id=?")) {
            ps.setLong(1, amount);
            ps.setLong(2, Instant.now().toEpochMilli());
            ps.setString(3, accountId);
            ps.executeUpdate();
        }
    }

    public void insertTx(java.sql.Connection c, String id, String actor, String source, String target, long amount, String reason, long tax) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO currency_transactions(transaction_id,actor_uuid,source_account,target_account,amount,reason,tax,created_at) VALUES(?,?,?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, actor);
            ps.setString(3, source);
            ps.setString(4, target);
            ps.setLong(5, amount);
            ps.setString(6, reason);
            ps.setLong(7, tax);
            ps.setLong(8, Instant.now().toEpochMilli());
            ps.executeUpdate();
        }
    }
}
