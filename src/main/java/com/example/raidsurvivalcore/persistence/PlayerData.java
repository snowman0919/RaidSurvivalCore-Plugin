package com.example.raidsurvivalcore.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class PlayerData {
    private final UUID uuid;
    private Duration newbiePlaytime;
    private String lastDeathWorld;
    private double lastDeathX;
    private double lastDeathY;
    private double lastDeathZ;
    private Instant lastDeathAt;
    private long tokens;
    private boolean dirty;

    public PlayerData(UUID uuid, Duration newbiePlaytime, long tokens) {
        this.uuid = uuid;
        this.newbiePlaytime = newbiePlaytime;
        this.tokens = tokens;
    }

    public UUID uuid() { return uuid; }
    public Duration newbiePlaytime() { return newbiePlaytime; }
    public long tokens() { return tokens; }
    public boolean dirty() { return dirty; }
    public String lastDeathWorld() { return lastDeathWorld; }
    public double lastDeathX() { return lastDeathX; }
    public double lastDeathY() { return lastDeathY; }
    public double lastDeathZ() { return lastDeathZ; }
    public Instant lastDeathAt() { return lastDeathAt; }

    public void setNewbiePlaytime(Duration newbiePlaytime) {
        this.newbiePlaytime = newbiePlaytime;
        this.dirty = true;
    }

    public void addTokens(long amount) {
        tokens += amount;
        dirty = true;
    }

    public boolean spendTokens(long amount) {
        if (tokens < amount) return false;
        tokens -= amount;
        dirty = true;
        return true;
    }

    public void setLastDeath(String world, double x, double y, double z, Instant at) {
        lastDeathWorld = world;
        lastDeathX = x;
        lastDeathY = y;
        lastDeathZ = z;
        lastDeathAt = at;
        dirty = true;
    }

    public void markClean() {
        dirty = false;
    }
}
