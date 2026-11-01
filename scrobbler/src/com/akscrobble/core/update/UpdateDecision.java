package com.akscrobble.core.update;

public enum UpdateDecision {
    NONE, OPTIONAL, REQUIRED, REJECTED;

    public static UpdateDecision decide(long installedCode, UpdateManifest m, String allowedUrlPrefix) {
        if (m.versionCode <= installedCode) return NONE;
        if (allowedUrlPrefix == null || !allowedUrlPrefix.startsWith("https://") || !allowedUrlPrefix.endsWith("/")
                || m.url == null || !m.url.startsWith(allowedUrlPrefix) || m.url.contains("..")
                || m.url.indexOf('@') >= 0 || m.url.indexOf('\\') >= 0 || hasWhitespace(m.url)) return REJECTED;
        if (m.updateRequired || (m.minSupportedCode != null && installedCode < m.minSupportedCode.longValue())) return REQUIRED;
        return OPTIONAL;
    }

    private static boolean hasWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) if (Character.isWhitespace(value.charAt(i))) return true;
        return false;
    }
}
