package com.akscrobble.core;

public enum LaunchRoute {
    SETUP, AUTH, HOME;

    public static LaunchRoute decide(Credentials credentials) {
        if (credentials == null || credentials.apiKey.length() == 0
                || credentials.apiSecret.length() == 0) return SETUP;
        if (credentials.sessionKey.length() == 0) return AUTH;
        return HOME;
    }
}
