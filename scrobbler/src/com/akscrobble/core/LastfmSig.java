package com.akscrobble.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

public final class LastfmSig {
    public static String hexMd5(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(32);
            for (byte b : digest) out.append(String.format("%02x", b & 0xff));
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is unavailable", e);
        }
    }

    public static String sign(Map<String, String> params, String secret) {
        TreeMap<String, String> sorted = new TreeMap<String, String>(params);
        StringBuilder plain = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            String key = entry.getKey();
            if ("api_sig".equals(key) || "format".equals(key) || "callback".equals(key)) continue;
            plain.append(key).append(entry.getValue());
        }
        plain.append(secret);
        return hexMd5(plain.toString());
    }

    private LastfmSig() {}
}
