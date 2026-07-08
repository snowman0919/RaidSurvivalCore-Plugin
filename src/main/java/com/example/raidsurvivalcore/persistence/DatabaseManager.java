package com.example.raidsurvivalcore.persistence;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

public final class DatabaseManager {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RaidCore-Database");
        t.setDaemon(true);
        return t;
    });
    private String jdbcUrl;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void start(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        jdbcUrl = "jdbc:sqlite:" + file.getAbsolutePath();
        Future<?> future = executor.submit(this::initSchema);
        try {
            future.get();
        } catch (Exception e) {
            logger.severe("Failed to initialize SQLite database: " + e.getMessage());
        }
    }

    public Connection connection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
            s.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    public ExecutorService executor() {
        return executor;
    }

    private void initSchema() {
        try (Connection c = connection(); Statement s = c.createStatement()) {
            c.setAutoCommit(false);
            s.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            s.executeUpdate("INSERT INTO schema_version(version) SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM schema_version)");
            migrateV1(s);
            int version = currentVersion(s);
            if (version < 2) {
                migrateV2(s);
                s.executeUpdate("UPDATE schema_version SET version = 2");
                version = 2;
            }
            if (version < 3) {
                migrateV3(s);
                s.executeUpdate("UPDATE schema_version SET version = 3");
                version = 3;
            }
            if (version < 4) {
                migrateV4(s);
                s.executeUpdate("UPDATE schema_version SET version = 4");
                version = 4;
            }
            if (version < 5) {
                migrateV5(s);
                s.executeUpdate("UPDATE schema_version SET version = 5");
            }
            c.commit();
        } catch (SQLException e) {
            logger.severe("RaidSurvivalCore SQLite schema initialization failed in plugin database. This is not vanilla player.dat: " + e.getMessage());
        }
    }

    private int currentVersion(Statement s) throws SQLException {
        try (ResultSet rs = s.executeQuery("SELECT MAX(version) FROM schema_version")) {
            return rs.next() ? rs.getInt(1) : 1;
        }
    }

    private void migrateV1(Statement s) throws SQLException {
        s.executeUpdate("CREATE TABLE IF NOT EXISTS player_data (uuid TEXT PRIMARY KEY, newbie_playtime_ms INTEGER NOT NULL DEFAULT 0 CHECK(newbie_playtime_ms >= 0), tokens INTEGER NOT NULL DEFAULT 0 CHECK(tokens >= 0), death_world TEXT, death_x REAL, death_y REAL, death_z REAL, death_at INTEGER)");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS bounties (target_uuid TEXT PRIMARY KEY, amount INTEGER NOT NULL DEFAULT 0 CHECK(amount >= 0))");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS kill_records (killer_uuid TEXT NOT NULL, victim_uuid TEXT NOT NULL, killed_at INTEGER NOT NULL)");
    }

    private void migrateV2(Statement s) throws SQLException {
        s.executeUpdate("CREATE TABLE IF NOT EXISTS tribes (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, normalized_name TEXT NOT NULL UNIQUE, tag TEXT NOT NULL, owner_uuid TEXT NOT NULL, level INTEGER NOT NULL DEFAULT 1 CHECK(level >= 1), total_experience INTEGER NOT NULL DEFAULT 0 CHECK(total_experience >= 0), spendable_currency INTEGER NOT NULL DEFAULT 0 CHECK(spendable_currency >= 0), locked_currency INTEGER NOT NULL DEFAULT 0 CHECK(locked_currency >= 0), created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, disband_state TEXT NOT NULL DEFAULT 'ACTIVE')");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS tribe_members (tribe_id INTEGER NOT NULL REFERENCES tribes(id) ON DELETE CASCADE, player_uuid TEXT NOT NULL UNIQUE, role TEXT NOT NULL, joined_at INTEGER NOT NULL, contribution_experience INTEGER NOT NULL DEFAULT 0 CHECK(contribution_experience >= 0), contribution_currency INTEGER NOT NULL DEFAULT 0 CHECK(contribution_currency >= 0), loyalty INTEGER NOT NULL DEFAULT 0, last_active_at INTEGER NOT NULL, transfer_cooldown_until INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(tribe_id, player_uuid))");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS tribe_member_roles (role TEXT PRIMARY KEY, rank_weight INTEGER NOT NULL)");
        s.executeUpdate("INSERT OR IGNORE INTO tribe_member_roles(role, rank_weight) VALUES ('OWNER', 100),('OFFICER', 80),('MEMBER', 50),('RECRUIT', 20),('PRISONER', 10)");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS tribe_experience (tribe_id INTEGER NOT NULL REFERENCES tribes(id) ON DELETE CASCADE, category TEXT NOT NULL, amount INTEGER NOT NULL DEFAULT 0 CHECK(amount >= 0), PRIMARY KEY(tribe_id, category))");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS tribe_experience_events (id INTEGER PRIMARY KEY AUTOINCREMENT, tribe_id INTEGER NOT NULL REFERENCES tribes(id) ON DELETE CASCADE, player_uuid TEXT, category TEXT NOT NULL, amount INTEGER NOT NULL CHECK(amount >= 0), reason TEXT NOT NULL, created_at INTEGER NOT NULL)");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS tribe_cores (core_id TEXT PRIMARY KEY, tribe_id INTEGER NOT NULL REFERENCES tribes(id) ON DELETE CASCADE, world_uuid TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, tier INTEGER NOT NULL DEFAULT 1 CHECK(tier >= 1), max_health REAL NOT NULL CHECK(max_health > 0), current_health REAL NOT NULL CHECK(current_health >= 0), state TEXT NOT NULL, created_at INTEGER NOT NULL, last_damaged_at INTEGER, destroyed_at INTEGER, UNIQUE(world_uuid, x, y, z))");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS tribe_relations (tribe_a INTEGER NOT NULL REFERENCES tribes(id) ON DELETE CASCADE, tribe_b INTEGER NOT NULL REFERENCES tribes(id) ON DELETE CASCADE, relation TEXT NOT NULL, updated_at INTEGER NOT NULL, CHECK(tribe_a < tribe_b), PRIMARY KEY(tribe_a, tribe_b))");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS tribe_wars (id INTEGER PRIMARY KEY AUTOINCREMENT, attacker_tribe_id INTEGER NOT NULL REFERENCES tribes(id), defender_tribe_id INTEGER NOT NULL REFERENCES tribes(id), state TEXT NOT NULL, declared_at INTEGER NOT NULL, preparation_ends_at INTEGER NOT NULL, active_ends_at INTEGER NOT NULL, cooldown_ends_at INTEGER NOT NULL, UNIQUE(attacker_tribe_id, defender_tribe_id, state))");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS tribe_invitations (tribe_id INTEGER NOT NULL REFERENCES tribes(id) ON DELETE CASCADE, player_uuid TEXT NOT NULL, invited_by_uuid TEXT NOT NULL, expires_at INTEGER NOT NULL, PRIMARY KEY(tribe_id, player_uuid))");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS tribe_capture_pressure (victim_uuid TEXT NOT NULL, attacker_tribe_id INTEGER NOT NULL REFERENCES tribes(id) ON DELETE CASCADE, points REAL NOT NULL CHECK(points >= 0), expires_at INTEGER NOT NULL, PRIMARY KEY(victim_uuid, attacker_tribe_id))");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS tribe_audit_log (id INTEGER PRIMARY KEY AUTOINCREMENT, tribe_id INTEGER REFERENCES tribes(id) ON DELETE SET NULL, actor_uuid TEXT, target_uuid TEXT, action TEXT NOT NULL, detail TEXT NOT NULL, created_at INTEGER NOT NULL)");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS currency_accounts (account_id TEXT PRIMARY KEY, account_type TEXT NOT NULL, balance INTEGER NOT NULL DEFAULT 0 CHECK(balance >= 0), updated_at INTEGER NOT NULL)");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS currency_transactions (transaction_id TEXT PRIMARY KEY, actor_uuid TEXT, source_account TEXT, target_account TEXT, amount INTEGER NOT NULL CHECK(amount >= 0), reason TEXT NOT NULL, tax INTEGER NOT NULL DEFAULT 0 CHECK(tax >= 0), created_at INTEGER NOT NULL)");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS tribe_treasury_transactions (transaction_id TEXT PRIMARY KEY, tribe_id INTEGER NOT NULL REFERENCES tribes(id) ON DELETE CASCADE, actor_uuid TEXT, amount INTEGER NOT NULL, reason TEXT NOT NULL, created_at INTEGER NOT NULL)");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS player_advancement_cache (player_uuid TEXT PRIMARY KEY, completed_count INTEGER NOT NULL DEFAULT 0 CHECK(completed_count >= 0), score INTEGER NOT NULL DEFAULT 0 CHECK(score >= 0), updated_at INTEGER NOT NULL)");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS player_activity_limits (player_uuid TEXT NOT NULL, bucket TEXT NOT NULL, window_start INTEGER NOT NULL, amount INTEGER NOT NULL DEFAULT 0 CHECK(amount >= 0), PRIMARY KEY(player_uuid, bucket, window_start))");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS core_damage_contributions (core_id TEXT NOT NULL REFERENCES tribe_cores(core_id) ON DELETE CASCADE, player_uuid TEXT NOT NULL, tribe_id INTEGER, damage REAL NOT NULL CHECK(damage >= 0), updated_at INTEGER NOT NULL, PRIMARY KEY(core_id, player_uuid))");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS core_destruction_events (event_id TEXT PRIMARY KEY, core_id TEXT NOT NULL, attacker_tribe_id INTEGER, defender_tribe_id INTEGER NOT NULL, xp_transferred INTEGER NOT NULL CHECK(xp_transferred >= 0), currency_looted INTEGER NOT NULL CHECK(currency_looted >= 0), created_at INTEGER NOT NULL)");
    }

    private void migrateV3(Statement s) throws SQLException {
        s.executeUpdate("CREATE TABLE IF NOT EXISTS tribe_homes (tribe_id INTEGER PRIMARY KEY REFERENCES tribes(id) ON DELETE CASCADE, world_uuid TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, updated_by_uuid TEXT NOT NULL, updated_at INTEGER NOT NULL)");
    }

    private void migrateV4(Statement s) throws SQLException {
        s.executeUpdate("CREATE TABLE IF NOT EXISTS bounty_items (id INTEGER PRIMARY KEY AUTOINCREMENT, target_uuid TEXT NOT NULL, item_blob TEXT NOT NULL, created_at INTEGER NOT NULL)");
        s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bounty_items_target ON bounty_items(target_uuid)");
    }

    private void migrateV5(Statement s) throws SQLException {
        s.executeUpdate("CREATE TABLE IF NOT EXISTS auction_listings (id INTEGER PRIMARY KEY AUTOINCREMENT, seller_uuid TEXT NOT NULL, item_blob TEXT NOT NULL, price INTEGER NOT NULL CHECK(price > 0), status TEXT NOT NULL DEFAULT 'ACTIVE', created_at INTEGER NOT NULL, buyer_uuid TEXT, sold_at INTEGER)");
        s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_auction_listings_status ON auction_listings(status, id)");
        s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_auction_listings_seller ON auction_listings(seller_uuid, status)");
    }

    public void shutdown() {
        executor.shutdown();
    }
}
