package com.akscrobble.core;

public final class QueuePolicy {
    public static final int MAX_ROWS = 1000;
    public static final int MAX_BATCH = 50;
    public static final int MAX_ATTEMPTS = 10;
    public static final long MAX_AGE_SEC = 14L * 24L * 3600L;
    public static final int MAX_DEAD_ROWS = 100;
    public static final long FUTURE_SLACK_SEC = 300L;

    public enum Outcome { DONE, RETRY, DEAD, AUTH_BLOCKED }

    public static long clampTimestamp(long startedAtSec, long nowSec) {
        return startedAtSec > nowSec + FUTURE_SLACK_SEC ? nowSec : startedAtSec;
    }

    public static boolean isTooOld(long startedAtSec, long nowSec) {
        return nowSec - startedAtSec > MAX_AGE_SEC;
    }

    public static Outcome classify(LastfmResponse response, int attempts) {
        if (response.isOk()) return Outcome.DONE;
        if (response.isAuthFailure()) return Outcome.AUTH_BLOCKED;
        if (response.isRetryable() && attempts + 1 < MAX_ATTEMPTS) return Outcome.RETRY;
        return Outcome.DEAD;
    }

    public static int evictCount(int currentRows) {
        return Math.max(0, currentRows - MAX_ROWS);
    }

    public static boolean isIgnoredPermanent(int ignoredCode) {
        return ignoredCode >= 1 && ignoredCode <= 5;
    }

    private QueuePolicy() {}
}
