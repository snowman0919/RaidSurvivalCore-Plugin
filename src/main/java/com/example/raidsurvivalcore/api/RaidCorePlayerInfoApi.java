package com.example.raidsurvivalcore.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface RaidCorePlayerInfoApi {
    CompletableFuture<RaidCorePlayerInfo> info(UUID player);
}
