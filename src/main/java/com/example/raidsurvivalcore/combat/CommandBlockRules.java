package com.example.raidsurvivalcore.combat;

import java.util.Locale;
import java.util.Set;

public final class CommandBlockRules {
    private CommandBlockRules() {
    }

    public static String rootCommand(String raw) {
        String value = raw == null ? "" : raw.strip();
        while (value.startsWith("/")) value = value.substring(1).stripLeading();
        int space = value.indexOf(' ');
        String root = space >= 0 ? value.substring(0, space) : value;
        int namespace = root.indexOf(':');
        if (namespace >= 0) root = root.substring(namespace + 1);
        return root.toLowerCase(Locale.ROOT);
    }

    public static boolean blocked(String raw, Set<String> blocked, Set<String> allowed) {
        String root = rootCommand(raw);
        if (root.isBlank()) return false;
        if (allowed.contains(root)) return false;
        return blocked.contains(root);
    }
}
