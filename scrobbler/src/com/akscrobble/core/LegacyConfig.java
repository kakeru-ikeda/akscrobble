package com.akscrobble.core;

import java.util.Locale;
import java.util.Map;

public final class LegacyConfig {
    private static final String[] LEGACY_COMMENTS = {
            "# Obtain API credentials at https://www.last.fm/api/account/create",
            "# Leave Callback URL and Application homepage empty there.",
            "#",
            "# Fill in api_key and api_secret below, then tap Load configuration",
            "# file on the main screen. These values take precedence over anything",
            "# entered in the app. Check api_key source: config in the status area.",
            "# Leave session_key empty; authentication obtains and stores it."
    };

    public static final class Result {
        public final String text;
        public final boolean changed;
        public final boolean meaningful;

        private Result(String text, boolean changed, boolean meaningful) {
            this.text = text;
            this.changed = changed;
            this.meaningful = meaningful;
        }
    }

    public static Result strip(String text) {
        if (text == null) return new Result("", false, false);

        StringBuilder kept = new StringBuilder(text.length());
        int offset = 0;
        if (text.startsWith("\uFEFF")) {
            kept.append('\uFEFF');
            offset = 1;
        }
        boolean changed = false;
        while (offset < text.length()) {
            int end = text.indexOf('\n', offset);
            int next = end < 0 ? text.length() : end + 1;
            String raw = text.substring(offset, next);
            String line = raw;
            if (line.endsWith("\n")) line = line.substring(0, line.length() - 1);
            if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
            if (shouldRemove(line)) {
                changed = true;
            } else {
                kept.append(raw);
            }
            offset = next;
        }

        String result = kept.toString();
        boolean meaningful = false;
        Map<String, String> values = ConfigFile.parse(result);
        for (String value : values.values()) {
            if (value != null && value.length() > 0) {
                meaningful = true;
                break;
            }
        }
        return new Result(result, changed, meaningful);
    }

    private static boolean shouldRemove(String line) {
        String trimmed = line.trim();
        if (trimmed.length() == 0 || trimmed.startsWith("#") || trimmed.startsWith(";")) {
            for (String comment : LEGACY_COMMENTS) {
                if (trimmed.equals(comment)) return true;
            }
            return false;
        }
        int equals = trimmed.indexOf('=');
        if (equals < 0) return false;
        String key = trimmed.substring(0, equals).trim().toLowerCase(Locale.US);
        return "api_key".equals(key) || "api_secret".equals(key) || "session_key".equals(key);
    }

    private LegacyConfig() {}
}
