package com.example.raidsurvivalcore.chat;

import java.text.Normalizer;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ChatObfuscator {
    private static final char[] HANGUL = "가나다라마바사아자차카타파하거너더러머버서어저처커터퍼허고노도로모보소오조초코토포호구누두루무부수우주추쿠투푸후기니디리미비시이지치키티피히".toCharArray();
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}|\\p{Cf}");
    private static final String BASIC_PUNCT = ".,!?;:'\"()[]{}-_/ ";

    public String sanitizePlain(String input) {
        String normalized = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFKC);
        return CONTROL.matcher(normalized).replaceAll("");
    }

    public String obfuscate(String plain, UUID messageId, UUID viewerId, boolean preserveSpaces, boolean preservePunctuation, boolean preserveLength) {
        String safe = sanitizePlain(plain);
        long seed = messageId.getMostSignificantBits() ^ messageId.getLeastSignificantBits() ^ viewerId.getMostSignificantBits() ^ Long.rotateLeft(viewerId.getLeastSignificantBits(), 17);
        SplittableRandom random = new SplittableRandom(seed);
        StringBuilder out = new StringBuilder(safe.length());
        for (int i = 0; i < safe.length(); i++) {
            char c = safe.charAt(i);
            if (Character.isWhitespace(c) && preserveSpaces) {
                out.append(c);
            } else if (preservePunctuation && BASIC_PUNCT.indexOf(c) >= 0 && c != '/' && c != ':' && c != '.') {
                out.append(c);
            } else if (!preserveLength && Character.isWhitespace(c)) {
                out.append(' ');
            } else {
                out.append(HANGUL[random.nextInt(HANGUL.length)]);
            }
        }
        return out.toString();
    }
}
