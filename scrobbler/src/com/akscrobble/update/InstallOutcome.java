package com.akscrobble.update;

import android.os.Handler;
import android.os.Looper;

public enum InstallOutcome {
    STARTED, NEEDS_PERMISSION, FAILED;

    public interface Listener {
        void onOutcome(InstallOutcome outcome, String message);
    }

    private static volatile Listener listener;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void setListener(Listener value) { listener = value; }

    static void notify(final InstallOutcome outcome, final String message) {
        MAIN.post(new Runnable() {
            @Override public void run() {
                Listener current = listener;
                if (current != null) current.onOutcome(outcome, message);
            }
        });
    }
}
