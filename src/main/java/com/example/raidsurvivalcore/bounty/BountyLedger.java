package com.example.raidsurvivalcore.bounty;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BountyLedger {
    private final Map<UUID, BigDecimal> bounties = new HashMap<>();

    public BigDecimal add(UUID target, BigDecimal amount) {
        BigDecimal next = bounties.getOrDefault(target, BigDecimal.ZERO).add(amount);
        bounties.put(target, next);
        return next;
    }

    public BigDecimal claim(UUID target, double multiplier) {
        BigDecimal amount = bounties.getOrDefault(target, BigDecimal.ZERO);
        if (amount.signum() <= 0 || multiplier <= 0.0) return BigDecimal.ZERO;
        bounties.remove(target);
        return amount.multiply(BigDecimal.valueOf(multiplier));
    }

    public BigDecimal get(UUID target) {
        return bounties.getOrDefault(target, BigDecimal.ZERO);
    }
}
