# RaidSurvivalCore

RaidSurvivalCore is a Paper plugin for PvP raid survival servers. It combines combat tags, proximity anti-stalling damage, newbie and respawn protection, random spawning, death-location notices, bounties, anti-farming rewards, tracking compasses, and combat restrictions in one plugin.

## Requirements

- Minecraft Java Edition 26.1.2
- Paper 26.1.2 build 72 or compatible
- Java 25
- Gradle Wrapper included

## Build

```bash
./gradlew clean build
```

The plugin jar is generated at:

```text
build/libs/RaidSurvivalCore-1.0.0.jar
```

## Install

1. Copy the jar into the server `plugins` directory.
2. Start the Paper server once to generate `config.yml` and `messages.yml`.
3. Edit configuration files.
4. Run `/raidcore reload` or restart the server.

Paper's plugin library loader downloads `org.xerial:sqlite-jdbc:3.50.3.0` from Maven for SQLite support. Vault is declared as `softdepend` and is optional; when Vault is absent, bounty payments use internal tokens.

## Features

- Proximity non-combat penalty: nearby eligible players who remain within the configured radius without valid PvP hits take increasing damage. The scan uses nearby entity lookup and normalized UUID pairs instead of all-player pair iteration.
- PvP Combat Tag: real player-sourced damage tags attacker and victim. Projectiles, owned entities, TNT source, fireworks where exposed by the API, and remembered attackers for lasting damage are handled where possible.
- Healing restrictions: combat-tagged players can have natural, saturation, potion, instant health, golden apple, enchanted golden apple, beacon-like, absorption, and totem behavior configured.
- Combat Log handling: tagged quits enter a reconnect grace window. If the player does not return, the tag is cleared and a punishment record is logged. Offline inventory death simulation is intentionally not forced because Bukkit does not expose a fully safe offline death/drop API.
- Random spawn: first join, respawn, and admin command random teleports use uniform area distribution and safe-location checks.
- New player protection: accumulates actual online playtime, blocks PvP both ways, can drop on first attack or Nether entry, and excludes protected players from proximity/tracker targeting.
- Respawn protection: separate short PvP protection after respawn.
- Death location: sends world, coordinates, time, expected item despawn, stores the last death, and can point recovery compasses.
- Bounties: `/bounty set`, `/bounty list`, `/bounty top`, `/bounty info`; data is stored in SQLite and paid to the last valid killer.
- Anti-farming: repeated killer/victim rewards are reduced and then suppressed inside the configured time window.
- Tracker compass: updates compass lodestones toward the nearest valid target without exact coordinates.
- Combat restrictions: blocks configured commands and selected movement escape mechanics while combat-tagged.
- Actionbar UI: priority is Combat Tag, Proximity, Newbie Protection, Respawn Protection.
- Tribes: persistent tribe creation, invitations, membership cache, PvP absorption, capture pressure, same-tribe PvP cancellation, and SQLite audit logs.
- Territory and cores: persistent core schema plus a chunk-indexed immutable territory snapshot used by random spawn and protection lookups.
- Economy: internal Crown accounts, player transfers, tribe treasury deposit/withdraw, admin adjustment, transaction reason codes, overflow checks, and audit transactions.
- Chat obfuscation: Paper `AsyncChatEvent` per-viewer renderer; same tribe and spy viewers see plaintext, others see deterministic obfuscation.

## Commands

- `/raidcore reload`
- `/raidcore status <player>`
- `/raidcore combat clear <player>`
- `/raidcore combat set <player> <seconds>`
- `/raidcore proximity clear <player>`
- `/raidcore spawn random <player>`
- `/raidcore spawn test`
- `/raidcore bounty clear <player>`
- `/raidcore protection clear <player>`
- `/raidcore debug proximity <player>`
- `/bounty set <player> <amount>`
- `/bounty list`
- `/bounty top`
- `/bounty info <player>`
- `/tribe create <name> <tag>`
- `/tribe invite <player>`
- `/tribe accept <tribe>`
- `/tribe leave`
- `/tribe info [tribe]`
- `/tribe members [tribe]`
- `/tribe top`
- `/tribe deposit <amount>`
- `/tribe withdraw <amount>`
- `/tribe chat`
- `/tribe war declare <tribe>`
- `/tc <message>`
- `/money`
- `/money pay <player> <amount>`
- `/money top`

## Permissions

- `raidcore.admin`
- `raidcore.reload`
- `raidcore.combat.bypass`
- `raidcore.proximity.bypass`
- `raidcore.randomspawn.bypass`
- `raidcore.newbie.bypass`
- `raidcore.respawnprotection.bypass`
- `raidcore.tracker.bypass`
- `raidcore.command.bypass`
- `raidcore.bounty.admin`
- `raidcore.tribe.create`
- `raidcore.tribe.invite`
- `raidcore.tribe.manage`
- `raidcore.tribe.treasury.withdraw`
- `raidcore.tribe.core.create`
- `raidcore.tribe.core.manage`
- `raidcore.tribe.war.declare`
- `raidcore.tribe.chat`
- `raidcore.chat.spy`
- `raidcore.economy.pay`
- `raidcore.economy.admin`
- `raidcore.tribe.admin`
- `raidcore.territory.bypass`
- `raidcore.core.damage.bypass`
- `raidcore.capture.bypass`

`raidcore.admin` includes all administrative child permissions.

## Configuration

Main sections:

- `proximity-penalty`
- `combat`
- `healing`
- `combat-log`
- `random-spawn`
- `new-player-protection`
- `respawn-protection`
- `death-location`
- `bounty`
- `anti-farming`
- `tracker`
- `database`
- `performance`
- `combat-restrictions`
- `tribes.yml`
- `territory.yml`
- `siege.yml`
- `economy.yml`
- `chat.yml`

Messages are in `messages.yml` and support MiniMessage syntax.

## SQLite

Default database path:

```text
plugins/RaidSurvivalCore/raidcore.db
```

Stored data includes new-player playtime, last death location, bounties, internal tokens, and repeated kill records. Database writes run on a dedicated executor; Bukkit objects are not manipulated from that executor.

Schema version 2 adds tribes, tribe members, experience events, cores, relations, wars, invitations, capture pressure, audit log, currency accounts, currency transactions, treasury transactions, advancement cache, activity limits, core damage contributions, and core destruction events.

## Operations Docs

- [Tribes](docs/TRIBES.md)
- [Territory and cores](docs/TERRITORY_AND_CORES.md)
- [Siege and war](docs/SIEGE_AND_WAR.md)
- [Economy](docs/ECONOMY.md)
- [Chat obfuscation](docs/CHAT_OBFUSCATION.md)
- [Admin recovery](docs/ADMIN_RECOVERY.md)

## Recommended Operation

- Keep proximity scan interval at 20 ticks unless the server has a very high player count.
- Add spawn and event worlds to `proximity-penalty.excluded-worlds`.
- Use bypass permissions for moderators and vanish-style staff roles.
- Test random-spawn forbidden biomes and fallback location before opening the server.
- Avoid running another plugin that also kills combat loggers unless one side is disabled.

## Known Limitations

- Bukkit/Paper does not provide a fully reliable offline-player death/drop API. Combat log punishment is recorded and the tag is cleared after grace; forced offline inventory drops require a server-specific extension.
- Vault detection is optional by design; this build always has an internal token fallback.
- Some explosion ownership paths depend on what Paper exposes in the damage event and entity source.
- The plugin avoids NMS and paperweight-userdev because no NMS access is required.

## Compatibility Notes

Other combat, protection, random teleport, bounty, or compass-tracker plugins may conflict if they cancel the same events or rewrite compass metadata. Prefer disabling overlapping modules in one plugin.
