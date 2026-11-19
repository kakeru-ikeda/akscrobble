package com.akscrobble.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

public final class QueuePolicy {
    public static final int MAX_ROWS = 1000;
    public static final int MAX_BATCH = 50;
    public static final int MAX_ATTEMPTS = 10;
    public static final long MAX_AGE_SEC = 14L * 24L * 3600L;
    public static final int MAX_DEAD_ROWS = 100;
    public static final long FUTURE_SLACK_SEC = 300L;

    public enum Outcome {
        DONE, RETRY, DEAD, AUTH_BLOCKED, HOLD, SIGNER_UNAVAILABLE, SERVICE_PROBLEM, PARTIAL_INVALID
    }

    public static long clampTimestamp(long startedAtSec, long nowSec) {
        return startedAtSec > nowSec + FUTURE_SLACK_SEC ? nowSec : startedAtSec;
    }

    public static boolean isTooOld(long startedAtSec, long nowSec) {
        return nowSec - startedAtSec > MAX_AGE_SEC;
    }

    public static Outcome classify(LastfmResponse response, int attempts) {
        if (response.isOk()) return Outcome.DONE;
        if (response.signerKind == SignResult.Kind.UNAVAILABLE) return Outcome.SIGNER_UNAVAILABLE;
        if (response.signerKind == SignResult.Kind.RETRYABLE) return Outcome.HOLD;
        if (response.signerKind == SignResult.Kind.INVALID_PARAMS
                && !response.signerInvalidIndices.isEmpty()) return Outcome.PARTIAL_INVALID;
        if (response.isAuthFailure()) return Outcome.AUTH_BLOCKED;
        if (response.isServiceProblem()) return Outcome.SERVICE_PROBLEM;
        if (response.isRetryable() && attempts + 1 < MAX_ATTEMPTS) return Outcome.RETRY;
        return Outcome.DEAD;
    }

    public static List<Integer> invalidIndices(LastfmResponse response, int batchSize) {
        TreeSet<Integer> valid = new TreeSet<Integer>();
        for (Integer index : response.signerInvalidIndices) {
            if (index != null && index.intValue() >= 0 && index.intValue() < batchSize) valid.add(index);
        }
        return Collections.unmodifiableList(new ArrayList<Integer>(valid));
    }

    public static int evictCount(int currentRows) {
        return Math.max(0, currentRows - MAX_ROWS);
    }

    public static boolean isIgnoredPermanent(int ignoredCode) {
        return ignoredCode >= 1 && ignoredCode <= 5;
    }

    private QueuePolicy() {}
}
