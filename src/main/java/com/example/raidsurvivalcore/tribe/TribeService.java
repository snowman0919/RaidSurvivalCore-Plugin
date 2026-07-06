package com.example.raidsurvivalcore.tribe;

import com.example.raidsurvivalcore.economy.CurrencyReason;
import com.example.raidsurvivalcore.economy.EconomyRules;
import com.example.raidsurvivalcore.economy.EconomyRules;
import com.example.raidsurvivalcore.economy.EconomyService;
import com.example.raidsurvivalcore.persistence.DatabaseManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public final class TribeService {
    private final DatabaseManager database;
    private final EconomyService economy;
    private final Logger logger;
    private final AtomicReference<TribeSnapshot> snapshot = new AtomicReference<>(new TribeSnapshot(Map.of(), Map.of(), Map.of()));
    private final long creationCost;

    public TribeService(DatabaseManager database, EconomyService economy, Logger logger, long creationCost) {
        this.database = database;
        this.economy = economy;
        this.logger = logger;
        this.creationCost = creationCost;
    }

    public TribeSnapshot snapshot() {
        return snapshot.get();
    }

    public CompletableFuture<Void> reloadSnapshot() {
        return CompletableFuture.runAsync(this::loadSnapshotSync, database.executor());
    }

    public CompletableFuture<CreateResult> create(UUID owner, String name, String tag, boolean conditionsMet) {
        return CompletableFuture.supplyAsync(() -> createSync(owner, name, tag, conditionsMet), database.executor()).whenComplete((r, e) -> loadSnapshotSync());
    }

    private CreateResult createSync(UUID owner, String name, String tag, boolean conditionsMet) {
        if (!conditionsMet) return CreateResult.failed("creation requirements");
        if (!TribeNameRules.validName(name, 16) || !TribeNameRules.validTag(tag, 5)) return CreateResult.failed("invalid name");
        String normalized = TribeNameRules.normalize(name);
        try (var c = database.connection()) {
            c.setAutoCommit(false);
            economy.ensureAccount(c, economy.personalAccount(owner), "PLAYER");
            try (PreparedStatement existing = c.prepareStatement("SELECT 1 FROM tribe_members WHERE player_uuid=?")) {
                existing.setString(1, owner.toString());
                if (existing.executeQuery().next()) {
                    c.rollback();
                    return CreateResult.failed("already in tribe");
                }
            }
            try (PreparedStatement existing = c.prepareStatement("SELECT 1 FROM tribes WHERE normalized_name=?")) {
                existing.setString(1, normalized);
                if (existing.executeQuery().next()) {
                    c.rollback();
                    return CreateResult.failed("duplicate name");
                }
            }
            long balance = economy.balanceSync(c, economy.personalAccount(owner));
            economy.setBalance(c, economy.personalAccount(owner), EconomyRules.checkedSubtract(balance, creationCost));
            long now = Instant.now().toEpochMilli();
            long tribeId;
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO tribes(name,normalized_name,tag,owner_uuid,level,total_experience,spendable_currency,locked_currency,created_at,updated_at,disband_state) VALUES(?,?,?,?,1,0,0,0,?,?, 'ACTIVE')", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name.strip());
                ps.setString(2, normalized);
                ps.setString(3, tag.strip());
                ps.setString(4, owner.toString());
                ps.setLong(5, now);
                ps.setLong(6, now);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("no generated tribe id");
                    tribeId = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO tribe_members(tribe_id,player_uuid,role,joined_at,last_active_at,transfer_cooldown_until) VALUES(?,?,?,?,?,0)")) {
                ps.setLong(1, tribeId);
                ps.setString(2, owner.toString());
                ps.setString(3, TribeRole.OWNER.name());
                ps.setLong(4, now);
                ps.setLong(5, now);
                ps.executeUpdate();
            }
            economy.insertTx(c, UUID.randomUUID().toString(), owner.toString(), economy.personalAccount(owner), "tribe:create:" + tribeId, creationCost, CurrencyReason.TRIBE_CREATION.name(), 0);
            audit(c, tribeId, owner, null, "TRIBE_CREATE", "name=" + name + ",tag=" + tag);
            c.commit();
            return CreateResult.success(tribeId);
        } catch (Exception e) {
            logger.warning("RaidSurvivalCore tribe creation transaction failed: " + e.getMessage());
            return CreateResult.failed(e.getMessage());
        }
    }

    public CompletableFuture<Boolean> invite(UUID actor, UUID target) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<TribeMember> member = snapshot().member(actor);
            if (member.isEmpty() || member.get().role().weight() < TribeRole.OFFICER.weight()) return false;
            try (var c = database.connection(); PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO tribe_invitations(tribe_id,player_uuid,invited_by_uuid,expires_at) VALUES(?,?,?,?)")) {
                ps.setLong(1, member.get().tribeId());
                ps.setString(2, target.toString());
                ps.setString(3, actor.toString());
                ps.setLong(4, Instant.now().plusSeconds(86400).toEpochMilli());
                ps.executeUpdate();
                audit(c, member.get().tribeId(), actor, target, "TRIBE_INVITE", "invited");
                return true;
            } catch (SQLException e) {
                logger.warning("RaidSurvivalCore tribe invite failed: " + e.getMessage());
                return false;
            }
        }, database.executor());
    }

    public CompletableFuture<Boolean> accept(UUID player, String tribeName) {
        return CompletableFuture.supplyAsync(() -> {
            Long tribeId = snapshot().normalizedNameToId().get(TribeNameRules.normalize(tribeName));
            if (tribeId == null || snapshot().member(player).isPresent()) return false;
            try (var c = database.connection()) {
                c.setAutoCommit(false);
                try (PreparedStatement inv = c.prepareStatement("DELETE FROM tribe_invitations WHERE tribe_id=? AND player_uuid=? AND expires_at>=?")) {
                    inv.setLong(1, tribeId);
                    inv.setString(2, player.toString());
                    inv.setLong(3, Instant.now().toEpochMilli());
                    if (inv.executeUpdate() == 0) {
                        c.rollback();
                        return false;
                    }
                }
                long now = Instant.now().toEpochMilli();
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO tribe_members(tribe_id,player_uuid,role,joined_at,last_active_at,transfer_cooldown_until) VALUES(?,?,?,?,?,0)")) {
                    ps.setLong(1, tribeId);
                    ps.setString(2, player.toString());
                    ps.setString(3, TribeRole.RECRUIT.name());
                    ps.setLong(4, now);
                    ps.setLong(5, now);
                    ps.executeUpdate();
                }
                audit(c, tribeId, player, player, "TRIBE_JOIN", "accepted invite");
                c.commit();
                return true;
            } catch (SQLException e) {
                logger.warning("RaidSurvivalCore tribe accept failed: " + e.getMessage());
                return false;
            }
        }, database.executor()).whenComplete((r, e) -> loadSnapshotSync());
    }

    public CompletableFuture<Boolean> addPrisoner(long tribeId, UUID player, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            if (snapshot().member(player).isPresent()) return false;
            try (var c = database.connection(); PreparedStatement ps = c.prepareStatement("INSERT INTO tribe_members(tribe_id,player_uuid,role,joined_at,last_active_at,transfer_cooldown_until) VALUES(?,?,?,?,?,?)")) {
                long now = Instant.now().toEpochMilli();
                ps.setLong(1, tribeId);
                ps.setString(2, player.toString());
                ps.setString(3, TribeRole.PRISONER.name());
                ps.setLong(4, now);
                ps.setLong(5, now);
                ps.setLong(6, Instant.now().plusSeconds(86400).toEpochMilli());
                ps.executeUpdate();
                audit(c, tribeId, null, player, "TRIBE_ABSORB", reason);
                return true;
            } catch (SQLException e) {
                logger.warning("RaidSurvivalCore prisoner add failed: " + e.getMessage());
                return false;
            }
        }, database.executor()).whenComplete((r, e) -> loadSnapshotSync());
    }

    public CompletableFuture<Boolean> deposit(UUID player, long amount) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<TribeMember> member = snapshot().member(player);
            if (member.isEmpty() || amount <= 0) return false;
            try (var c = database.connection()) {
                c.setAutoCommit(false);
                economy.ensureAccount(c, economy.personalAccount(player), "PLAYER");
                long personal = economy.balanceSync(c, economy.personalAccount(player));
                economy.setBalance(c, economy.personalAccount(player), EconomyRules.checkedSubtract(personal, amount));
                try (PreparedStatement ps = c.prepareStatement("UPDATE tribes SET spendable_currency=spendable_currency+?, updated_at=? WHERE id=?")) {
                    ps.setLong(1, amount);
                    ps.setLong(2, Instant.now().toEpochMilli());
                    ps.setLong(3, member.get().tribeId());
                    ps.executeUpdate();
                }
                treasuryTx(c, member.get().tribeId(), player, amount, "TRIBE_DEPOSIT");
                economy.insertTx(c, UUID.randomUUID().toString(), player.toString(), economy.personalAccount(player), "tribe:" + member.get().tribeId(), amount, CurrencyReason.TRIBE_DEPOSIT.name(), 0);
                c.commit();
                return true;
            } catch (Exception e) {
                logger.warning("RaidSurvivalCore tribe deposit failed: " + e.getMessage());
                return false;
            }
        }, database.executor()).whenComplete((r, e) -> loadSnapshotSync());
    }

    public CompletableFuture<Boolean> withdraw(UUID player, long amount) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<TribeMember> member = snapshot().member(player);
            if (member.isEmpty() || !member.get().canManageTreasury() || amount <= 0) return false;
            try (var c = database.connection()) {
                c.setAutoCommit(false);
                long treasury;
                try (PreparedStatement ps = c.prepareStatement("SELECT spendable_currency FROM tribes WHERE id=?")) {
                    ps.setLong(1, member.get().tribeId());
                    try (ResultSet rs = ps.executeQuery()) {
                        treasury = rs.next() ? rs.getLong(1) : 0;
                    }
                }
                long nextTreasury = EconomyRules.checkedSubtract(treasury, amount);
                try (PreparedStatement ps = c.prepareStatement("UPDATE tribes SET spendable_currency=?, updated_at=? WHERE id=?")) {
                    ps.setLong(1, nextTreasury);
                    ps.setLong(2, Instant.now().toEpochMilli());
                    ps.setLong(3, member.get().tribeId());
                    ps.executeUpdate();
                }
                economy.ensureAccount(c, economy.personalAccount(player), "PLAYER");
                economy.setBalance(c, economy.personalAccount(player), EconomyRules.checkedAdd(economy.balanceSync(c, economy.personalAccount(player)), amount, Long.MAX_VALUE));
                treasuryTx(c, member.get().tribeId(), player, -amount, "TRIBE_WITHDRAW");
                economy.insertTx(c, UUID.randomUUID().toString(), player.toString(), "tribe:" + member.get().tribeId(), economy.personalAccount(player), amount, CurrencyReason.TRIBE_WITHDRAW.name(), 0);
                c.commit();
                return true;
            } catch (Exception e) {
                logger.warning("RaidSurvivalCore tribe withdraw failed: " + e.getMessage());
                return false;
            }
        }, database.executor()).whenComplete((r, e) -> loadSnapshotSync());
    }

    public CompletableFuture<Boolean> declareWar(UUID actor, String defenderName) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<TribeMember> attacker = snapshot().member(actor);
            Long defender = snapshot().normalizedNameToId().get(TribeNameRules.normalize(defenderName));
            if (attacker.isEmpty() || defender == null || attacker.get().tribeId() == defender) return false;
            try (var c = database.connection(); PreparedStatement ps = c.prepareStatement("INSERT INTO tribe_wars(attacker_tribe_id,defender_tribe_id,state,declared_at,preparation_ends_at,active_ends_at,cooldown_ends_at) VALUES(?,?,?,?,?,?,?)")) {
                long now = Instant.now().toEpochMilli();
                ps.setLong(1, attacker.get().tribeId());
                ps.setLong(2, defender);
                ps.setString(3, "PREPARATION");
                ps.setLong(4, now);
                ps.setLong(5, Instant.now().plusSeconds(1800).toEpochMilli());
                ps.setLong(6, Instant.now().plusSeconds(1800 + 86400).toEpochMilli());
                ps.setLong(7, Instant.now().plusSeconds(1800 + 86400 + 43200).toEpochMilli());
                ps.executeUpdate();
                audit(c, attacker.get().tribeId(), actor, null, "WAR_DECLARE", "defender=" + defenderName);
                return true;
            } catch (SQLException e) {
                logger.warning("RaidSurvivalCore war declaration failed: " + e.getMessage());
                return false;
            }
        }, database.executor());
    }

    public CompletableFuture<String> topSummary() {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder out = new StringBuilder();
            try (var c = database.connection(); PreparedStatement ps = c.prepareStatement("SELECT name, total_experience, level FROM tribes ORDER BY total_experience DESC, id ASC LIMIT 10"); ResultSet rs = ps.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    out.append(rank++).append(". ").append(rs.getString(1)).append(" Lv.").append(rs.getInt(3)).append(" XP ").append(rs.getLong(2)).append('\n');
                }
            } catch (SQLException e) {
                logger.warning("RaidSurvivalCore tribe top failed: " + e.getMessage());
            }
            return out.isEmpty() ? "No tribes." : out.toString().stripTrailing();
        }, database.executor());
    }

    public void handlePvpDeath(UUID killer, UUID victim, boolean victimNewbieProtected) {
        if (killer == null || victim == null || killer.equals(victim) || victimNewbieProtected) return;
        TribeSnapshot snap = snapshot();
        Optional<TribeMember> killerMember = snap.member(killer);
        Optional<TribeMember> victimMember = snap.member(victim);
        if (killerMember.isEmpty()) return;
        if (victimMember.isEmpty()) {
            addPrisoner(killerMember.get().tribeId(), victim, "pvp absorption by " + killer);
            return;
        }
        if (killerMember.get().tribeId() == victimMember.get().tribeId()) return;
        if (victimMember.get().role() == TribeRole.OWNER) return;
        CompletableFuture.runAsync(() -> addCapturePressureSync(killerMember.get(), victimMember.get(), killer, victim), database.executor()).whenComplete((r, e) -> loadSnapshotSync());
    }

    private void addCapturePressureSync(TribeMember killerMember, TribeMember victimMember, UUID killer, UUID victim) {
        double required = victimMember.role() == TribeRole.OFFICER ? 10.0 : 5.0;
        long expires = Instant.now().plusSeconds(24 * 3600L).toEpochMilli();
        try (var c = database.connection()) {
            c.setAutoCommit(false);
            double points;
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO tribe_capture_pressure(victim_uuid,attacker_tribe_id,points,expires_at) VALUES(?,?,1,?) ON CONFLICT(victim_uuid,attacker_tribe_id) DO UPDATE SET points=points+1, expires_at=excluded.expires_at")) {
                ps.setString(1, victim.toString());
                ps.setLong(2, killerMember.tribeId());
                ps.setLong(3, expires);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT points FROM tribe_capture_pressure WHERE victim_uuid=? AND attacker_tribe_id=? AND expires_at>=?")) {
                ps.setString(1, victim.toString());
                ps.setLong(2, killerMember.tribeId());
                ps.setLong(3, Instant.now().toEpochMilli());
                try (ResultSet rs = ps.executeQuery()) {
                    points = rs.next() ? rs.getDouble(1) : 0;
                }
            }
            audit(c, victimMember.tribeId(), killer, victim, "CAPTURE_PRESSURE", "attackerTribe=" + killerMember.tribeId() + ",points=" + points);
            if (points >= required && victimMember.transferCooldownUntil().toEpochMilli() <= Instant.now().toEpochMilli()) {
                try (PreparedStatement del = c.prepareStatement("DELETE FROM tribe_members WHERE player_uuid=?")) {
                    del.setString(1, victim.toString());
                    del.executeUpdate();
                }
                try (PreparedStatement add = c.prepareStatement("INSERT INTO tribe_members(tribe_id,player_uuid,role,joined_at,last_active_at,transfer_cooldown_until) VALUES(?,?,?,?,?,?)")) {
                    long now = Instant.now().toEpochMilli();
                    add.setLong(1, killerMember.tribeId());
                    add.setString(2, victim.toString());
                    add.setString(3, TribeRole.PRISONER.name());
                    add.setLong(4, now);
                    add.setLong(5, now);
                    add.setLong(6, Instant.now().plusSeconds(72 * 3600L).toEpochMilli());
                    add.executeUpdate();
                }
                audit(c, killerMember.tribeId(), killer, victim, "TRIBE_CAPTURE", "from=" + victimMember.tribeId() + ",to=" + killerMember.tribeId());
            }
            c.commit();
        } catch (SQLException e) {
            logger.warning("RaidSurvivalCore capture pressure transaction failed: " + e.getMessage());
        }
    }

    private void loadSnapshotSync() {
        Map<UUID, TribeMember> members = new HashMap<>();
        Map<Long, Tribe> tribes = new HashMap<>();
        Map<String, Long> names = new HashMap<>();
        try (var c = database.connection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM tribes"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Tribe tribe = new Tribe(rs.getLong("id"), rs.getString("name"), rs.getString("normalized_name"), rs.getString("tag"), UUID.fromString(rs.getString("owner_uuid")), rs.getInt("level"), rs.getLong("total_experience"), rs.getLong("spendable_currency"), rs.getLong("locked_currency"), Instant.ofEpochMilli(rs.getLong("created_at")), Instant.ofEpochMilli(rs.getLong("updated_at")), rs.getString("disband_state"));
                    tribes.put(tribe.id(), tribe);
                    names.put(tribe.normalizedName(), tribe.id());
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM tribe_members"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID player = UUID.fromString(rs.getString("player_uuid"));
                    members.put(player, new TribeMember(rs.getLong("tribe_id"), player, TribeRole.valueOf(rs.getString("role")), Instant.ofEpochMilli(rs.getLong("joined_at")), rs.getLong("contribution_experience"), rs.getLong("contribution_currency"), rs.getInt("loyalty"), Instant.ofEpochMilli(rs.getLong("last_active_at")), Instant.ofEpochMilli(rs.getLong("transfer_cooldown_until"))));
                }
            }
            snapshot.set(new TribeSnapshot(Map.copyOf(members), Map.copyOf(tribes), Map.copyOf(names)));
        } catch (SQLException e) {
            logger.warning("RaidSurvivalCore tribe snapshot load failed; keeping previous immutable snapshot: " + e.getMessage());
        }
    }

    private void audit(java.sql.Connection c, long tribeId, UUID actor, UUID target, String action, String detail) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO tribe_audit_log(tribe_id,actor_uuid,target_uuid,action,detail,created_at) VALUES(?,?,?,?,?,?)")) {
            ps.setLong(1, tribeId);
            ps.setString(2, actor == null ? null : actor.toString());
            ps.setString(3, target == null ? null : target.toString());
            ps.setString(4, action);
            ps.setString(5, detail);
            ps.setLong(6, Instant.now().toEpochMilli());
            ps.executeUpdate();
        }
    }

    private void treasuryTx(java.sql.Connection c, long tribeId, UUID actor, long amount, String reason) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO tribe_treasury_transactions(transaction_id,tribe_id,actor_uuid,amount,reason,created_at) VALUES(?,?,?,?,?,?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setLong(2, tribeId);
            ps.setString(3, actor.toString());
            ps.setLong(4, amount);
            ps.setString(5, reason);
            ps.setLong(6, Instant.now().toEpochMilli());
            ps.executeUpdate();
        }
    }

    public record CreateResult(boolean success, String reason, long tribeId) {
        public static CreateResult success(long tribeId) {
            return new CreateResult(true, "", tribeId);
        }

        public static CreateResult failed(String reason) {
            return new CreateResult(false, reason, -1);
        }
    }
}
