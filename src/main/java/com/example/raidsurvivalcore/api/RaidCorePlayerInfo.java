package com.example.raidsurvivalcore.api;

public record RaidCorePlayerInfo(
    long balance,
    String tribeName,
    String tribeTag,
    String tribeRole,
    boolean combatTagged,
    long combatSeconds
) {
    public boolean hasTribe() {
        return tribeName != null && !tribeName.isBlank();
    }
}
