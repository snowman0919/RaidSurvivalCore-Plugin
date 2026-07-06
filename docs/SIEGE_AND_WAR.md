# Siege And War

Siege calculations are implemented as domain rules and stored in SQLite tables for wars, cores, damage contributions, and destruction events.

Core damage supports separate multipliers for non-war infiltration and official war. The default non-war multiplier is lower, but not zero, so infiltration remains possible. Official war damage uses the full multiplier and can receive group bonuses.

Core regeneration starts only after the configured delay and does not run while the core is under attack or breached.

Core destruction rewards are capped by configured ratios and maximum values. Destruction event IDs are unique, so payout logic can be idempotent around `core_destruction_events`.
