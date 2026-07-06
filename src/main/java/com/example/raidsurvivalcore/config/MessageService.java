package com.example.raidsurvivalcore.config;

import java.io.File;
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
        messages = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
    }

    public Component message(String key, Map<String, String> placeholders) {
        String raw = messages.getString(key, "<red>Missing message: " + key + "</red>");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return mini.deserialize(raw);
    }

    public Component prefixed(String key, Map<String, String> placeholders) {
        String prefix = messages.getString("prefix", "");
        String raw = prefix + messages.getString(key, "<red>Missing message: " + key + "</red>");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return mini.deserialize(raw);
    }
}
