package com.example.raidsurvivalcore.economy;

public final class EconomyRules {
    private EconomyRules() {
    }

    public static long checkedAdd(long balance, long amount, long max) {
        if (amount < 0) throw new IllegalArgumentException("amount must be non-negative");
        if (balance > max - amount) throw new ArithmeticException("balance overflow");
        return balance + amount;
    }

    public static long checkedSubtract(long balance, long amount) {
        if (amount < 0) throw new IllegalArgumentException("amount must be non-negative");
        if (balance < amount) throw new IllegalStateException("insufficient balance");
        return balance - amount;
    }

    public static long tax(long amount, double rate) {
        if (amount <= 0) return 0;
        double clamped = Math.max(0.0, Math.min(1.0, rate));
        return Math.max(0, Math.round(amount * clamped));
    }
}
