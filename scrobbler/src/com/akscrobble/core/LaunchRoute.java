package com.akscrobble.core;

public enum LaunchRoute {
    SETUP, AUTH, HOME;

    public static LaunchRoute decide(Credentials credentials, boolean setupDismissed) {
        if (credentials == null || !credentials.isComplete())
            return setupDismissed ? AUTH : SETUP;
        return HOME;
    }
}
