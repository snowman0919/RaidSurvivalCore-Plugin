package com.example.raidsurvivalcore.tribe;

public enum TribeRole {
    OWNER(100),
    OFFICER(80),
    MEMBER(50),
    RECRUIT(20),
    PRISONER(10);

    private final int weight;

    TribeRole(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
