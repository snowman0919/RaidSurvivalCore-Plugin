package com.example.raidsurvivalcore.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class PlayerDataRepository {
    private final DatabaseManager database;
    private final Logger logger;
    private final long startingTokens;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataRepository(DatabaseManager database, Logger logger, long startingTokens) {
        this.database = database;
        this.logger = logger;
        this.startingTokens = startingTokens;
    }

    public CompletableFuture<PlayerData> load(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (var c = database.connection();
                 PreparedStatement ps = c.prepareStatement("SELECT * FROM player_data WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    PlayerData data;
                    if (rs.next()) {
                        data = new PlayerData(uuid, Duration.ofMillis(rs.getLong("newbie_playtime_ms")), rs.getLong("tokens"));
                        long deathAt = rs.getLong("death_at");
                        if (!rs.wasNull()) {
                            data.setLastDeath(rs.getString("death_world"), rs.getDouble("death_x"), rs.getDouble("death_y"), rs.getDouble("death_z"), Instant.ofEpochMilli(deathAt));
                            data.markClean();
                        }
                    } else {
                        data = new PlayerData(uuid, Duration.ZERO, startingTokens);
                        data.addTokens(0);
                    }
                    cache.put(uuid, data);
                    return data;
                }
            } catch (SQLException e) {
                logger.warning("RaidSurvivalCore SQLite player profile load failed for " + uuid + ". This is plugin storage, not vanilla player.dat: " + e.getMessage());
                throw new IllegalStateException("plugin player profile load failed", e);
            }
        }, database.executor());
    }

    public PlayerData getOrCreate(UUID uuid) {
        return cache.computeIfAbsent(uuid, id -> new PlayerData(id, Duration.ZERO, startingTokens));
    }

    public void unload(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data != null) save(data);
    }

    public CompletableFuture<Void> save(PlayerData data) {
        return CompletableFuture.runAsync(() -> {
            try (var c = database.connection();
                 PreparedStatement ps = c.prepareStatement("INSERT INTO player_data(uuid,newbie_playtime_ms,tokens,death_world,death_x,death_y,death_z,death_at) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET newbie_playtime_ms=excluded.newbie_playtime_ms,tokens=excluded.tokens,death_world=excluded.death_world,death_x=excluded.death_x,death_y=excluded.death_y,death_z=excluded.death_z,death_at=excluded.death_at")) {
                ps.setString(1, data.uuid().toString());
                ps.setLong(2, data.newbiePlaytime().toMillis());
                ps.setLong(3, data.tokens());
                ps.setString(4, data.lastDeathWorld());
                ps.setDouble(5, data.lastDeathX());
                ps.setDouble(6, data.lastDeathY());
                ps.setDouble(7, data.lastDeathZ());
                if (data.lastDeathAt() == null) ps.setObject(8, null); else ps.setLong(8, data.lastDeathAt().toEpochMilli());
                ps.executeUpdate();
                data.markClean();
            } catch (SQLException e) {
                logger.warning("RaidSurvivalCore SQLite player profile save failed for " + data.uuid() + ". Dirty data remains cached for retry: " + e.getMessage());
            }
        }, database.executor());
    }

    public void saveDirty() {
        cache.values().stream().filter(PlayerData::dirty).forEach(this::save);
    }

    public void flushAll() {
        cache.values().forEach(data -> save(data).join());
    }
}
