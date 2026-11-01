package com.akscrobble.core;

public enum SourceStatus {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    DISABLED,
    NOT_INSTALLED,
    STOPPED;

    public static SourceStatus of(boolean serviceEnabled, boolean sourceEnabled,
            boolean installed, String linkState) {
        if (!serviceEnabled) return STOPPED;
        if (!installed) return NOT_INSTALLED;
        if (!sourceEnabled) return DISABLED;
        if ("connected".equals(linkState)) return CONNECTED;
        if ("connecting".equals(linkState)) return CONNECTING;
        if ("absent".equals(linkState)) return NOT_INSTALLED;
        return DISCONNECTED;
    }
}
