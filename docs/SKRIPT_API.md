# RaidSurvivalCore Skript Economy API

RaidSurvivalCore registers a Bukkit service:

```java
com.example.raidsurvivalcore.api.RaidCoreEconomyApi
```

Methods return `CompletableFuture`:

```java
CompletableFuture<Long> balance(UUID player)
CompletableFuture<Boolean> deposit(UUID player, long amount, String reason)
CompletableFuture<Boolean> withdraw(UUID player, long amount, String reason)
CompletableFuture<Boolean> set(UUID player, long amount, String reason)
CompletableFuture<Boolean> transfer(UUID from, UUID to, long amount, String reason)
```

All changes use the same SQLite economy tables as `/money`, `/shop`, `/trade`, and `/bounty`.
Script reasons are stored in `currency_transactions.reason` as `SKRIPT:<reason>`.

The API is intentionally async. Do not block the Minecraft main thread waiting for the future.
