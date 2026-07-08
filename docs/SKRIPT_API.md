# RaidSurvivalCore Skript Economy API

RaidSurvivalCore registers a Bukkit service:

```java
com.example.raidsurvivalcore.api.RaidCoreEconomyApi
com.example.raidsurvivalcore.api.RaidCorePlayerInfoApi
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

Player info API:

```java
CompletableFuture<RaidCorePlayerInfo> info(UUID player)
```

`RaidCorePlayerInfo` fields:

```java
long balance()
String tribeName()
String tribeTag()
String tribeRole()
boolean combatTagged()
long combatSeconds()
```

The bundled Skript scoreboard uses `RaidCorePlayerInfoApi` through skript-reflect.
