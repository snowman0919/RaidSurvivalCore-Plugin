package com.example.raidsurvivalcore.territory;

import com.example.raidsurvivalcore.persistence.DatabaseManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public final class TerritoryService {
    private final DatabaseManager database;
    private final Logger logger;
    private final AtomicReference<TerritoryIndex> index = new AtomicReference<>(new TerritoryIndex());
    private final AtomicReference<List<TribeCore>> cores = new AtomicReference<>(List.of());

    public TerritoryService(DatabaseManager database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    public CompletableFuture<Void> reloadSnapshot() {
        return CompletableFuture.runAsync(this::loadSync, database.executor());
    }

    public List<TribeCore> coresAt(UUID worldUuid, int x, int z) {
        return index.get().coresAt(worldUuid, x, z);
    }

    public boolean nearAnyCore(UUID worldUuid, int x, int z, int minDistance) {
        long minSq = (long) minDistance * minDistance;
        for (TribeCore core : cores.get()) {
            if (!core.worldUuid().equals(worldUuid)) continue;
            long dx = (long) x - core.x();
            long dz = (long) z - core.z();
            if (dx * dx + dz * dz <= minSq) return true;
        }
        return false;
    }

    public CompletableFuture<String> createCore(long tribeId, UUID worldUuid, int x, int y, int z, int radius, double maxHealth) {
        return CompletableFuture.supplyAsync(() -> {
            String id = UUID.randomUUID().toString();
            try (var c = database.connection(); PreparedStatement ps = c.prepareStatement("INSERT INTO tribe_cores(core_id,tribe_id,world_uuid,x,y,z,tier,max_health,current_health,state,created_at) VALUES(?,?,?,?,?,?,1,?,?,?,?)")) {
                long now = Instant.now().toEpochMilli();
                ps.setString(1, id);
                ps.setLong(2, tribeId);
                ps.setString(3, worldUuid.toString());
                ps.setInt(4, x);
                ps.setInt(5, y);
                ps.setInt(6, z);
                ps.setDouble(7, maxHealth);
                ps.setDouble(8, maxHealth);
                ps.setString(9, CoreState.ACTIVE.name());
                ps.setLong(10, now);
                ps.executeUpdate();
                loadSync();
                return id;
            } catch (SQLException e) {
                logger.warning("RaidSurvivalCore core create failed: " + e.getMessage());
                return "";
            }
        }, database.executor());
    }

    private void loadSync() {
        List<TribeCore> loaded = new ArrayList<>();
        try (var c = database.connection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM tribe_cores WHERE state IN ('ACTIVE','UNDER_ATTACK','BREACHED')"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long last = rs.getLong("last_damaged_at");
                long destroyed = rs.getLong("destroyed_at");
                loaded.add(new TribeCore(rs.getString("core_id"), rs.getLong("tribe_id"), UUID.fromString(rs.getString("world_uuid")), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), rs.getInt("tier"), rs.getDouble("max_health"), rs.getDouble("current_health"), CoreState.valueOf(rs.getString("state")), Instant.ofEpochMilli(rs.getLong("created_at")), rs.wasNull() || last == 0 ? null : Instant.ofEpochMilli(last), destroyed == 0 ? null : Instant.ofEpochMilli(destroyed), 32));
            }
            TerritoryIndex next = new TerritoryIndex();
            next.rebuild(loaded);
            cores.set(List.copyOf(loaded));
            index.set(next);
        } catch (SQLException e) {
            logger.warning("RaidSurvivalCore territory snapshot load failed; keeping previous index: " + e.getMessage());
        }
    }
}
