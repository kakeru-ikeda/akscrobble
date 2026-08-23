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
                + "# Obtain API credentials at https://www.last.fm/api/account/create\n"
                + "# Leave Callback URL and Application homepage empty there.\n"
                + "#\n"
                + "# Fill in api_key and api_secret below. This file is read every time\n"
                + "# credentials are resolved, so no button needs to be pressed, and its\n"
                + "# values take precedence over anything entered in the app.\n"
                + "# Leave session_key empty; authentication obtains and stores it.\n"
                + "#\n"
                + "# Lines starting with # or ; are comments.\n"
                + "api_key=\n"
                + "api_secret=\n"
                + "session_key=\n"
                + "user_name=\n";
    }

    private ConfigFile() {}
}
