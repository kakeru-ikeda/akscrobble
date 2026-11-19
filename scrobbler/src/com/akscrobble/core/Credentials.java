package com.akscrobble.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

public final class Credentials {
    public final String sessionKey;
    public final String sessionApiKey;

    private Credentials(String sessionKey, String sessionApiKey) {
        this.sessionKey = sessionKey;
        this.sessionApiKey = sessionApiKey;
    }

    public static Credentials resolve(Map<String, String> file, Map<String, String> prefs) {
        return new Credentials(choose("session_key", file, prefs),
                choose("session_api_key", file, prefs));
    }

    public boolean isComplete() {
        return sessionKey.length() > 0 && sessionApiKey.length() > 0;
    }

    public String fingerprint() {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest((sessionApiKey + "|" + sessionKey).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(32);
            for (byte value : digest) result.append(String.format("%02x", value & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is unavailable", e);
        }
    }

    private static String choose(String field, Map<String, String> file,
            Map<String, String> prefs) {
        String value = value(file, field);
        return value.length() > 0 ? value : value(prefs, field);
    }

    private static String value(Map<String, String> values, String key) {
        if (values == null) return "";
        String value = values.get(key);
        return value == null ? "" : value.trim();
    }
}
