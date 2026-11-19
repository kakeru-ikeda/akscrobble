package com.akscrobble.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ConfigFile {
    public static Map<String, String> parse(String text) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        if (text == null) return values;
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        String[] lines = text.split("\\r?\\n", -1);
        for (String raw : lines) {
            String line = raw.trim();
            if (line.length() == 0 || line.startsWith("#") || line.startsWith(";")) continue;
            int equals = line.indexOf('=');
            if (equals < 0) continue;
            String key = line.substring(0, equals).trim().toLowerCase(Locale.US);
            if (key.length() == 0) continue;
            values.put(key, line.substring(equals + 1).trim());
        }
        return values;
    }

    public static String template() {
        return "# AKScrobble configuration\n"
                + "#\n"
                + "# Last.fm authorization is done in the app. No API key is needed.\n"
                + "# Leave session_key and session_api_key empty; authorization stores them.\n"
                + "#\n"
                + "# Lines starting with # or ; are comments.\n"
                + "session_key=\n"
                + "session_api_key=\n"
                + "user_name=\n";
    }

    private ConfigFile() {}
}
