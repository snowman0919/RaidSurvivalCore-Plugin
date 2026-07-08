# User Commands and Permissions

## Default users

These commands are intended for normal players:

```text
/money
/money pay <player> <amount>
/money top
/shop list
/shop info <item>
/shop buy <item> [count]
/shop sell <item> [count]
/trade request <player>
/trade accept <player>
/trade item <player> <amount>
/trade money <player> <amount>
/trade cancel <player>
/bounty set <player> <amount>
/bounty item <player> [amount]
/bounty list
/bounty top
/bounty info <player>
/tribe create <name> <tag>
/tribe invite <player>
/tribe accept <tribe>
/tribe leave
/tribe disband
/tribe info
/tribe members
/tribe top
/tribe deposit <amount>
/tribe withdraw <amount>
/tribe chat
/tc <message>
```

Recommended default permissions:

```text
raidcore.economy.pay
raidcore.tribe.create
raidcore.tribe.invite
raidcore.tribe.chat
```

Most user-facing commands currently have no extra permission gate beyond the plugin command defaults.
Use LuckPerms command restrictions if a specific server group should not access shop, trade, or bounty.

## Staff

Recommended staff/admin permissions:

```text
raidcore.admin
raidcore.reload
raidcore.economy.admin
raidcore.bounty.admin
raidcore.tribe.admin
raidcore.chat.spy
raidcore.command.bypass
raidcore.combat.bypass
raidcore.proximity.bypass
raidcore.newbie.bypass
raidcore.respawnprotection.bypass
raidcore.randomspawn.bypass
```

Console and RCON can grant Crown with:

```text
money pay <player> <amount>
```

Players still use `/money pay <player> <amount>` as player-to-player transfer.
