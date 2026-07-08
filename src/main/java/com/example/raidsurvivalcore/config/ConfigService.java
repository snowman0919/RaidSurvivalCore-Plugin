package com.example.raidsurvivalcore.config;

import com.example.raidsurvivalcore.model.RegionBox;
import java.time.Duration;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigService {
    private final JavaPlugin plugin;
    private final Logger logger;
    private RaidCoreConfig current;

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void reload() {
        com.example.raidsurvivalcore.util.ResourceInstaller.saveIfMissing(plugin, "config.yml");
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        current = new RaidCoreConfig(
            new RaidCoreConfig.Proximity(
                c.getBoolean("proximity-penalty.enabled", true),
                positive(c.getDouble("proximity-penalty.radius", 15.0), 15.0, "proximity radius"),
                positive(c.getDouble("proximity-penalty.y-threshold", 8.0), 8.0, "proximity y-threshold"),
                positive(c.getDouble("proximity-penalty.always-detect-radius", 8.0), 8.0, "always-detect radius"),
                seconds(c.getLong("proximity-penalty.grace-period-seconds", 20), 0, "proximity grace"),
                seconds(c.getLong("proximity-penalty.damage-interval-seconds", 5), 1, "damage interval"),
                nonNegative(c.getDouble("proximity-penalty.base-damage", 1.0), 1.0, "base damage"),
                nonNegative(c.getDouble("proximity-penalty.increase-per-stage", 0.5), 0.5, "increase per stage"),
                maxAtLeast(c.getDouble("proximity-penalty.max-damage", 8.0), c.getDouble("proximity-penalty.base-damage", 1.0), "max damage"),
                nonNegative(c.getDouble("proximity-penalty.extra-player-multiplier-step", 0.25), 0.25, "extra multiplier step"),
                Math.max(1.0, c.getDouble("proximity-penalty.max-multiplier", 2.0)),
                Math.max(1L, c.getLong("proximity-penalty.scan-interval-ticks", 20)),
                seconds(c.getLong("proximity-penalty.attack-exemption-seconds", 15), 0, "attack exemption"),
                seconds(c.getLong("proximity-penalty.reset-after-separated-seconds", 10), 0, "separation reset"),
                lowerSet(c.getStringList("proximity-penalty.excluded-worlds"))
            ),
            new RaidCoreConfig.Combat(
                c.getBoolean("combat.enabled", true),
                seconds(c.getLong("combat.tag-seconds", 20), 1, "combat tag"),
                seconds(c.getLong("combat.last-attacker-memory-seconds", 12), 1, "last attacker memory"),
                c.getBoolean("combat.actionbar", true),
                regionBoxes(c, "combat.safe-zones")
            ),
            new RaidCoreConfig.Healing(
                clampMultiplier(c.getDouble("healing.natural-regeneration", 0.0), "natural"),
                clampMultiplier(c.getDouble("healing.saturation-regeneration", 0.0), "saturation"),
                clampMultiplier(c.getDouble("healing.regeneration-effect", 0.5), "regeneration"),
                clampMultiplier(c.getDouble("healing.instant-health", 0.5), "instant health"),
                clampMultiplier(c.getDouble("healing.golden-apple", 0.5), "golden apple"),
                clampMultiplier(c.getDouble("healing.enchanted-golden-apple", 0.35), "enchanted golden apple"),
                clampMultiplier(c.getDouble("healing.beacon-regeneration", 0.0), "beacon"),
                clampMultiplier(c.getDouble("healing.absorption", 0.5), "absorption"),
                c.getBoolean("healing.allow-totem", true),
                c.getBoolean("healing.bypass-plugin-forced-heal", true)
            ),
            new RaidCoreConfig.CombatLog(c.getBoolean("combat-log.enabled", true), seconds(c.getLong("combat-log.reconnect-grace-seconds", 3), 0, "combat log grace"), c.getString("combat-log.action", "KILL"), c.getString("combat-log.bypass-permission", "raidcore.combat.bypass")),
            randomSpawn(c),
            new RaidCoreConfig.Newbie(c.getBoolean("new-player-protection.enabled", true), Duration.ofMinutes(Math.max(1, c.getLong("new-player-protection.playtime-minutes", 30))), c.getBoolean("new-player-protection.disable-on-attack", true), c.getBoolean("new-player-protection.disable-on-nether", true)),
            new RaidCoreConfig.Respawn(c.getBoolean("respawn-protection.enabled", true), seconds(c.getLong("respawn-protection.seconds", 30), 0, "respawn protection"), c.getBoolean("respawn-protection.disable-on-attack", true), c.getBoolean("respawn-protection.block-chest-open", false), c.getBoolean("respawn-protection.block-item-pickup", false), c.getBoolean("respawn-protection.block-portal", false)),
            new RaidCoreConfig.Death(c.getBoolean("death-location.enabled", true), c.getBoolean("death-location.recovery-compass", true), Math.max(1, c.getInt("death-location.item-despawn-minutes", 5)), timeZone(c.getString("death-location.time-zone", "Asia/Seoul"))),
            new RaidCoreConfig.Bounty(c.getBoolean("bounty.enabled", true), Math.max(0, c.getLong("bounty.min-amount", 10)), Math.max(c.getLong("bounty.min-amount", 10), c.getLong("bounty.max-amount", 1000000)), c.getBoolean("bounty.use-vault-if-present", true), Math.max(0, c.getLong("bounty.internal-starting-tokens", 0))),
            new RaidCoreConfig.AntiFarming(c.getBoolean("anti-farming.enabled", true), Duration.ofMinutes(Math.max(1, c.getLong("anti-farming.window-minutes", 30))), Math.max(0, c.getInt("anti-farming.full-reward-kills", 2)), Math.max(1, c.getInt("anti-farming.reduced-from-kill", 3)), Math.max(1, c.getInt("anti-farming.no-reward-from-kill", 5)), clampMultiplier(c.getDouble("anti-farming.reduced-multiplier", 0.5), "reduced reward")),
            new RaidCoreConfig.EnderChestLoot(c.getBoolean("ender-chest-loot.enabled", true), c.getBoolean("ender-chest-loot.pvp-only", true), clampMultiplier(c.getDouble("ender-chest-loot.drop-chance", 0.25), "ender chest drop chance"), Math.max(1, c.getInt("ender-chest-loot.max-stacks", 2)), clampMultiplier(c.getDouble("ender-chest-loot.stack-fraction", 0.5), "ender chest stack fraction")),
            new RaidCoreConfig.Advancements(c.getBoolean("advancements.disable-announcements", true)),
            new RaidCoreConfig.Tracker(c.getBoolean("tracker.enabled", true), seconds(c.getLong("tracker.update-seconds", 30), 1, "tracker update"), positive(c.getDouble("tracker.min-distance", 500), 500, "tracker min"), positive(c.getDouble("tracker.max-distance", 5000), 5000, "tracker max"), c.getBoolean("tracker.consume-token", false), Math.max(0, c.getLong("tracker.token-cost", 1)), c.getString("tracker.compass-name", "<gold>추적 나침반</gold>")),
            new RaidCoreConfig.Database(c.getString("database.file", "raidcore.db"), seconds(c.getLong("database.autosave-seconds", 120), 10, "autosave")),
            new RaidCoreConfig.Performance(Math.max(1, c.getLong("performance.actionbar-interval-ticks", 20)), seconds(c.getLong("performance.cleanup-interval-seconds", 5), 1, "cleanup")),
            new RaidCoreConfig.Restrictions(c.getBoolean("combat-restrictions.enabled", true), lowerSet(c.getStringList("combat-restrictions.blocked-commands")), lowerSet(c.getStringList("combat-restrictions.allowed-commands")), c.getBoolean("combat-restrictions.block-chorus-fruit", true), c.getBoolean("combat-restrictions.block-vehicle", true), c.getBoolean("combat-restrictions.block-nether-portal", true), c.getBoolean("combat-restrictions.block-end-portal", true), c.getBoolean("combat-restrictions.block-elytra", false), c.getBoolean("combat-restrictions.block-firework", false), c.getBoolean("combat-restrictions.block-ender-pearl", false))
        );
    }

    public RaidCoreConfig get() {
        return current;
    }

    private RaidCoreConfig.RandomSpawn randomSpawn(FileConfiguration c) {
        double min = positive(c.getDouble("random-spawn.min-radius", 500), 500, "random spawn min");
        double max = c.getDouble("random-spawn.max-radius", 5000);
        if (max <= min) {
            logger.warning("random-spawn.max-radius must be greater than min-radius; using " + (min + 1));
            max = min + 1;
        }
        return new RaidCoreConfig.RandomSpawn(c.getBoolean("random-spawn.enabled", true), c.getBoolean("random-spawn.first-join", true), c.getBoolean("random-spawn.respawn", true), c.getString("random-spawn.world", "world"), c.getDouble("random-spawn.center-x", 0), c.getDouble("random-spawn.center-z", 0), min, max, Math.max(1, c.getInt("random-spawn.max-attempts", 40)), positive(c.getDouble("random-spawn.min-distance-from-players", 64), 64, "spawn player distance"), c.getString("random-spawn.fallback.world", "world"), c.getDouble("random-spawn.fallback.x", 0.5), c.getDouble("random-spawn.fallback.y", 80), c.getDouble("random-spawn.fallback.z", 0.5), (float) c.getDouble("random-spawn.fallback.yaw", 0), (float) c.getDouble("random-spawn.fallback.pitch", 0), lowerSet(c.getStringList("random-spawn.forbidden-biomes")));
    }

    private Duration seconds(long value, long minimum, String name) {
        if (value < minimum) {
            logger.warning(name + " must be at least " + minimum + " seconds; using " + minimum);
            value = minimum;
        }
        return Duration.ofSeconds(value);
    }

    private double positive(double value, double fallback, String name) {
        if (value <= 0) {
            logger.warning(name + " must be positive; using " + fallback);
            return fallback;
        }
        return value;
    }

    private double nonNegative(double value, double fallback, String name) {
        if (value < 0) {
            logger.warning(name + " must be non-negative; using " + fallback);
            return fallback;
        }
        return value;
    }

    private double maxAtLeast(double max, double base, String name) {
        if (max < base) {
            logger.warning(name + " must be at least base damage; using base damage");
            return base;
        }
        return max;
    }

    private double clampMultiplier(double value, String name) {
        if (value < 0 || value > 1) {
            logger.warning(name + " multiplier must be between 0 and 1; clamping");
            return Math.max(0, Math.min(1, value));
        }
        return value;
    }

    private Set<String> lowerSet(java.util.List<String> values) {
        return values.stream().map(v -> v.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    private String timeZone(String raw) {
        try {
            return ZoneId.of(raw == null || raw.isBlank() ? "Asia/Seoul" : raw).getId();
        } catch (ZoneRulesException e) {
            logger.warning("death-location.time-zone is invalid; using Asia/Seoul");
            return "Asia/Seoul";
        }
    }

    private List<RegionBox> regionBoxes(FileConfiguration c, String path) {
        List<RegionBox> boxes = new ArrayList<>();
        for (Map<?, ?> entry : c.getMapList(path)) {
            Object worldValue = entry.get("world");
            String world = String.valueOf(worldValue == null ? "" : worldValue).strip();
            if (world.isBlank()) {
                logger.warning(path + " entry is missing world; skipping");
                continue;
            }
            boxes.add(new RegionBox(
                world,
                number(entry, "min-x", number(entry, "x1", 0.0)),
                number(entry, "min-y", number(entry, "y1", -64.0)),
                number(entry, "min-z", number(entry, "z1", 0.0)),
                number(entry, "max-x", number(entry, "x2", 0.0)),
                number(entry, "max-y", number(entry, "y2", 320.0)),
                number(entry, "max-z", number(entry, "z2", 0.0))
            ));
        }
        return List.copyOf(boxes);
    }

    private double number(Map<?, ?> entry, String key, double fallback) {
        Object value = entry.get(key);
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }
}
