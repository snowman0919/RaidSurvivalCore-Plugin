package com.example.raidsurvivalcore.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

public final class ResourceInstaller {
    private ResourceInstaller() {
    }

    public static void saveIfMissing(JavaPlugin plugin, String resourceName) {
        Path path = plugin.getDataFolder().toPath().resolve(resourceName);
        if (Files.exists(path)) return;
        plugin.saveResource(resourceName, false);
    }

    public static boolean verifyWritableDataPath(JavaPlugin plugin, Logger logger, String databaseFileName) {
        Path dataFolder = plugin.getDataFolder().toPath();
        try {
            Files.createDirectories(dataFolder);
            Path probe = dataFolder.resolve(".raidcore-write-test");
            Files.writeString(probe, "ok");
            Files.deleteIfExists(probe);
        } catch (IOException e) {
            logger.severe("RaidSurvivalCore plugin data folder is not writable: " + dataFolder.toAbsolutePath() + " (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            return false;
        }
        Path db = dataFolder.resolve(databaseFileName);
        if (Files.exists(db) && !Files.isReadable(db)) {
            logger.severe("RaidSurvivalCore SQLite database is not readable: " + db.toAbsolutePath());
            return false;
        }
        if (Files.exists(db) && !Files.isWritable(db)) {
            logger.severe("RaidSurvivalCore SQLite database is not writable: " + db.toAbsolutePath());
            return false;
        }
        return true;
    }
}
