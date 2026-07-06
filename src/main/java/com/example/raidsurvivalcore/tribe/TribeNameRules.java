package com.example.raidsurvivalcore.tribe;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TribeNameRules {
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}|\\p{Cf}");
    private static final Pattern MINIMESSAGE = Pattern.compile("<[^>]+>");
    private static final Pattern COMMAND_CONFUSING = Pattern.compile("^[./\\\\:;].*|.*[\\n\\r\\t].*");

    private TribeNameRules() {
    }

    public static String normalize(String name) {
        if (name == null) return "";
        String noTags = MINIMESSAGE.matcher(name).replaceAll("");
        String cleaned = CONTROL.matcher(noTags).replaceAll("");
        cleaned = Normalizer.normalize(cleaned, Normalizer.Form.NFKC).strip();
        cleaned = cleaned.replaceAll("\\s+", " ");
        return cleaned.toLowerCase(Locale.ROOT);
    }

    public static boolean validName(String name, int maxLength) {
        String normalized = normalize(name);
        return !normalized.isBlank()
            && normalized.length() <= maxLength
            && !COMMAND_CONFUSING.matcher(name == null ? "" : name).matches()
            && normalized.chars().anyMatch(Character::isLetterOrDigit);
    }

    public static boolean validTag(String tag, int maxLength) {
        String normalized = normalize(tag);
        return !normalized.isBlank()
            && normalized.length() <= maxLength
            && !COMMAND_CONFUSING.matcher(tag == null ? "" : tag).matches()
            && normalized.matches("[\\p{L}\\p{N}_-]+");
    }
}
