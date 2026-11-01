package com.akscrobble.core.update;

import java.util.Map;
import java.util.regex.Pattern;

public final class UpdateManifest {
    private static final Pattern SHA = Pattern.compile("[0-9a-fA-F]{64}");
    public final String version;
    public final long versionCode;
    public final String url;
    public final long size;
    public final String sha256;
    public final String notes;
    public final Long minSupportedCode;
    public final boolean updateRequired;

    private UpdateManifest(String version, long versionCode, String url, long size, String sha256,
            String notes, Long minSupportedCode, boolean updateRequired) {
        this.version = version; this.versionCode = versionCode; this.url = url; this.size = size;
        this.sha256 = sha256; this.notes = notes; this.minSupportedCode = minSupportedCode;
        this.updateRequired = updateRequired;
    }

    public static UpdateManifest parse(String json) {
        Object parsed = MiniJson.parse(json);
        if (!(parsed instanceof Map)) throw new IllegalArgumentException("manifest must be an object");
        Map<?, ?> map = (Map<?, ?>) parsed;
        String version = string(map, "version", true);
        long versionCode = positiveInteger(map, "version_code");
        String url = string(map, "url", true);
        long size = positiveInteger(map, "size");
        String sha = string(map, "sha256", true);
        if (!SHA.matcher(sha).matches()) throw new IllegalArgumentException("invalid sha256");
        String notes = string(map, "notes", false);
        Long min = optionalInteger(map, "min_supported_code");
        Object requiredValue = map.get("update_required");
        if (map.containsKey("update_required") && !(requiredValue instanceof Boolean)) throw new IllegalArgumentException("invalid update_required");
        return new UpdateManifest(version, versionCode, url, size, sha.toLowerCase(), notes, min,
                Boolean.TRUE.equals(requiredValue));
    }

    private static String string(Map<?, ?> map, String key, boolean required) {
        Object value = map.get(key);
        if (value == null && !required) return null;
        if (!(value instanceof String)) throw new IllegalArgumentException("invalid " + key);
        return (String) value;
    }

    private static long positiveInteger(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Long) || ((Long) value).longValue() < 1) throw new IllegalArgumentException("invalid " + key);
        return ((Long) value).longValue();
    }

    private static Long optionalInteger(Map<?, ?> map, String key) {
        if (!map.containsKey(key) || map.get(key) == null) return null;
        Object value = map.get(key);
        if (!(value instanceof Long) || ((Long) value).longValue() < 1) throw new IllegalArgumentException("invalid " + key);
        return (Long) value;
    }
}
