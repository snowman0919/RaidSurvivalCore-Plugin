package com.example.raidsurvivalcore.api;

import com.example.raidsurvivalcore.economy.EconomyService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RaidCoreEconomyApiImpl implements RaidCoreEconomyApi {
    private final EconomyService economy;

    public RaidCoreEconomyApiImpl(EconomyService economy) {
        this.economy = economy;
    }

    @Override
    public CompletableFuture<Long> balance(UUID player) {
        return economy.balance(player);
    }

    @Override
    public CompletableFuture<Boolean> deposit(UUID player, long amount, String reason) {
        return economy.scriptAdjust(null, player, amount, "add", reason);
    }

    @Override
    public CompletableFuture<Boolean> withdraw(UUID player, long amount, String reason) {
        return economy.scriptAdjust(null, player, amount, "remove", reason);
    }

    @Override
    public CompletableFuture<Boolean> set(UUID player, long amount, String reason) {
        return economy.scriptAdjust(null, player, amount, "set", reason);
    }

    @Override
    public CompletableFuture<Boolean> transfer(UUID from, UUID to, long amount, String reason) {
        return economy.scriptTransfer(from, to, amount, reason);
    }
}
