package com.akscrobble.core;

public final class BackoffPolicy {
    private static final long[] DELAYS_MS = {
        30_000L, 120_000L, 600_000L, 1_800_000L, 3_600_000L
    };

    public static long nextDelayMs(int consecutiveFailures) {
        if (consecutiveFailures <= 0) return 0L;
        return DELAYS_MS[Math.min(consecutiveFailures, DELAYS_MS.length) - 1];
    }

    private BackoffPolicy() {}
}
