package com.example.raidsurvivalcore.proximity;

import java.time.Instant;

public final class ProximityState {
    private Instant firstSeen;
    private Instant lastDamage;
    private Instant lastValidAttack;
    private Instant separatedSince;

    public ProximityState(Instant now) {
        this.firstSeen = now;
    }

    public Instant firstSeen() {
        return firstSeen;
    }

    public Instant lastDamage() {
        return lastDamage;
    }

    public void markDamage(Instant now) {
        lastDamage = now;
    }

    public Instant lastValidAttack() {
        return lastValidAttack;
    }

    public void markValidAttack(Instant now) {
        lastValidAttack = now;
        firstSeen = now;
        separatedSince = null;
        lastDamage = null;
    }

    public Instant separatedSince() {
        return separatedSince;
    }

    public void markSeen(Instant now) {
        if (firstSeen == null) firstSeen = now;
        separatedSince = null;
    }

    public void markSeparated(Instant now) {
        if (separatedSince == null) separatedSince = now;
    }
}
