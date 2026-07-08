package com.example.raidsurvivalcore.shop;

import com.example.raidsurvivalcore.util.ResourceInstaller;
import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShopService {
    private final JavaPlugin plugin;
    private final Logger logger;
    private volatile boolean enabled;
    private volatile Map<String, ShopEntry> entries = Map.of();

    public ShopService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void reload() {
        ResourceInstaller.saveIfMissing(plugin, "shop.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "shop.yml"));
        enabled = yml.getBoolean("enabled", true);
        Map<String, ShopEntry> loaded = new ConcurrentHashMap<>();
        ConfigurationSection items = yml.getConfigurationSection("items");
        if (items == null) {
            entries = Map.of();
            return;
        }
        for (String id : items.getKeys(false)) {
            String path = "items." + id + ".";
            Material material = Material.matchMaterial(yml.getString(path + "material", ""));
            if (material == null || !material.isItem()) {
                logger.warning("shop.yml item '" + id + "' has invalid material; skipping");
                continue;
            }
            int amount = Math.max(1, yml.getInt(path + "amount", 1));
            int max = Math.max(1, yml.getInt(path + "max-per-transaction", material.getMaxStackSize()));
            long buy = yml.getLong(path + "buy-price", -1);
            long sell = yml.getLong(path + "sell-price", -1);
            loaded.put(id.toLowerCase(Locale.ROOT), new ShopEntry(id.toLowerCase(Locale.ROOT), material, amount, buy, sell, max));
        }
        entries = Map.copyOf(loaded);
    }

    public boolean enabled() {
        return enabled;
    }

    public ShopEntry entry(String id) {
        return entries.get(id.toLowerCase(Locale.ROOT));
    }

    public List<ShopEntry> entries() {
        return entries.values().stream().sorted(Comparator.comparing(ShopEntry::id)).toList();
    }
}
