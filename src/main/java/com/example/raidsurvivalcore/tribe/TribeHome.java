package com.example.raidsurvivalcore.tribe;

import java.util.UUID;

public record TribeHome(
    long tribeId,
    UUID worldUuid,
    double x,
    double y,
    double z,
    float yaw,
    float pitch
) {
}
