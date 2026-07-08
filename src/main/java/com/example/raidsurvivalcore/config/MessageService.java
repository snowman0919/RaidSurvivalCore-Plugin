package com.example.raidsurvivalcore.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.example.raidsurvivalcore.util.ResourceInstaller;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MessageService {
    private final JavaPlugin plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private FileConfiguration messages;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        ResourceInstaller.saveIfMissing(plugin, "messages.yml");
        File file = new File(plugin.getDataFolder(), "messages.yml");
        messages = YamlConfiguration.loadConfiguration(file);
        mergeBundledDefaults(file);
    }

    public Component message(String key, Map<String, String> placeholders) {
        String raw = rawMessage(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return mini.deserialize(raw);
    }

    public Component prefixed(String key, Map<String, String> placeholders) {
        String prefix = messages.getString("prefix", "");
        String raw = prefix + rawMessage(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return mini.deserialize(raw);
    }

    private String rawMessage(String key) {
        return messages.getString(key, "<red>Missing message: " + key + "</red>");
    }

    private void mergeBundledDefaults(File file) {
        try (InputStream input = plugin.getResource("messages.yml")) {
            if (input == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(key) || messages.contains(key)) continue;
                messages.set(key, defaults.get(key));
                changed = true;
            }
            if (changed) messages.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to merge bundled message defaults: " + e.getMessage());
        }
    }
}
