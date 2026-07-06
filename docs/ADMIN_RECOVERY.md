# Admin Recovery

## Core Block Recovery

If a core exists in SQLite but the world block is missing, inspect `tribe_cores` and recreate the block at its recorded world UUID and coordinates, or mark the core `DISABLED` after confirming it is invalid.

## Disable Incorrect Core

Update `tribe_cores.state` to `DISABLED` and reload the plugin. The territory snapshot will stop indexing it.

## Owner Recovery

Find the target tribe in `tribes`, then update `tribes.owner_uuid` and the corresponding `tribe_members.role` to `OWNER`. Ensure the previous owner is demoted if needed.

## Duplicate Or Orphaned Members

The database prevents simultaneous membership via `UNIQUE(player_uuid)`. If manual edits created inconsistent data, keep one row per player and delete orphaned rows whose `tribe_id` has no row in `tribes`.

## Failed Currency Transaction Inspection

Compare `currency_accounts` with recent `currency_transactions`. Transactions have stable IDs and reason codes. Do not edit balances without also inserting an `ADMIN_ADJUSTMENT` transaction.

## Database Backup And Restore

Stop the server, copy `plugins/RaidSurvivalCore/raidcore.db`, then restart. Restore by stopping the server and replacing the database file with a known-good backup.

## File Permission Diagnosis

The plugin verifies its own data folder and SQLite file are readable and writable on enable. It does not read, write, chmod, chown, copy, or repair vanilla world player data.

The server error below is a Minecraft server filesystem ownership or permission issue outside this plugin:

```text
Failed to copy the player.dat file
java.nio.file.AccessDeniedException: ./world/players/data/<uuid>.dat
```

Fix that at the operating-system or server host level. RaidSurvivalCore must not change world folder ownership automatically.
