package com.akscrobble.core;

import java.util.HashMap;
import java.util.Map;

public final class Credentials {
    public final String apiKey;
    public final String apiSecret;
    public final String sessionKey;
    private final Map<String, String> sources;

    private Credentials(String apiKey, String apiSecret, String sessionKey,
            Map<String, String> sources) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.sessionKey = sessionKey;
        this.sources = sources;
    }

    public static Credentials resolve(Map<String, String> file, Map<String, String> prefs,
            String builtinKey, String builtinSecret) {
        Map<String, String> sources = new HashMap<String, String>();
        String key = choose("api_key", file, prefs, builtinKey, sources);
        String secret = choose("api_secret", file, prefs, builtinSecret, sources);
        String session = choose("session_key", file, prefs, "", sources);
        return new Credentials(key, secret, session, sources);
    }

    public boolean isComplete() {
        return apiKey.length() > 0 && apiSecret.length() > 0 && sessionKey.length() > 0;
    }

    public String source(String field) {
        String source = sources.get(normalizeField(field));
        return source == null ? "none" : source;
    }

    private static String choose(String field, Map<String, String> file,
            Map<String, String> prefs, String builtin, Map<String, String> sources) {
        String value = value(file, field);
        if (value.length() > 0) { sources.put(field, "config"); return value; }
        value = value(prefs, field);
        if (value.length() > 0) { sources.put(field, "prefs"); return value; }
        value = clean(builtin);
        if (value.length() > 0) { sources.put(field, "builtin"); return value; }
        sources.put(field, "none");
        return "";
    }

    private static String value(Map<String, String> values, String key) {
        return values == null ? "" : clean(values.get(key));
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    private static String normalizeField(String field) {
        if ("apiKey".equals(field)) return "api_key";
        if ("apiSecret".equals(field)) return "api_secret";
        if ("sessionKey".equals(field)) return "session_key";
        return field == null ? "" : field.toLowerCase();
    }
}
