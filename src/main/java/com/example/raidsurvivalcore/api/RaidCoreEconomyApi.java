package com.example.raidsurvivalcore.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface RaidCoreEconomyApi {
    CompletableFuture<Long> balance(UUID player);

    CompletableFuture<Boolean> deposit(UUID player, long amount, String reason);

    CompletableFuture<Boolean> withdraw(UUID player, long amount, String reason);

    CompletableFuture<Boolean> set(UUID player, long amount, String reason);

    CompletableFuture<Boolean> transfer(UUID from, UUID to, long amount, String reason);
}
