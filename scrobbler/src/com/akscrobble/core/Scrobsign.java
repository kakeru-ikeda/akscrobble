package com.akscrobble.core;

import java.util.Map;
import java.util.TreeMap;

public final class Scrobsign {
    public static final String SIGN_URL = "https://scrobsign.sukisuki.club/v1/akscrobble/sign";
    public static final int CONNECT_TIMEOUT_MS = 10000;
    public static final int READ_TIMEOUT_MS = 15000;
    public static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private static final String[] RESERVED = {"method", "api_key", "api_sig", "format", "callback"};

    public static TreeMap<String, String> signableParams(Map<String, String> lastfmParams) {
        if (lastfmParams == null) throw new IllegalArgumentException("params are null");
        TreeMap<String, String> result = new TreeMap<String, String>();
        for (Map.Entry<String, String> entry : lastfmParams.entrySet()) {
            String key = entry.getKey();
            if (key == null) throw new IllegalArgumentException("param key is null");
            if (!isReserved(key)) result.put(key, entry.getValue());
        }
        return result;
    }

    public static String requestJson(String method, Map<String, String> params) {
        if (method == null || method.length() == 0) throw new IllegalArgumentException("method is empty");
        if (params == null) throw new IllegalArgumentException("params are null");
        TreeMap<String, String> sorted = new TreeMap<String, String>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key == null) throw new IllegalArgumentException("param key is null");
            if (isReserved(key)) throw new IllegalArgumentException("reserved param key");
            if (entry.getValue() == null) throw new IllegalArgumentException("param value is null");
            sorted.put(key, entry.getValue());
        }
        StringBuilder json = new StringBuilder("{\"method\":");
        appendQuoted(json, method);
        json.append(",\"params\":{");
        boolean first = true;
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!first) json.append(',');
            first = false;
            appendQuoted(json, entry.getKey());
            json.append(':');
            appendQuoted(json, entry.getValue());
        }
        return json.append("}}").toString();
    }

    private static void appendQuoted(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') out.append("\\\"");
            else if (c == '\\') out.append("\\\\");
            else if (c <= 0x1f) {
                out.append("\\u00");
                out.append(Character.forDigit((c >>> 4) & 0xf, 16));
                out.append(Character.forDigit(c & 0xf, 16));
            } else out.append(c);
        }
        out.append('"');
    }

    private static boolean isReserved(String key) {
        for (String reserved : RESERVED) if (reserved.equals(key)) return true;
        return false;
    }

    private Scrobsign() {}
}
