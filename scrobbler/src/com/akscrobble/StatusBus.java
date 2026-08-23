package com.akscrobble;

import java.util.LinkedHashMap;
import java.util.Map;

public final class StatusBus {
    private static final LinkedHashMap<String, String> VALUES =
            new LinkedHashMap<String, String>();
    private static Runnable listener;

    public static long serviceStartedElapsed;

    public static void put(String key, String value) {
        Runnable currentListener;
        synchronized (StatusBus.class) {
            VALUES.put(key, value);
            currentListener = listener;
        }
        if (currentListener != null) {
            currentListener.run();
        }
    }

    public static synchronized String snapshotText() {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> entry : VALUES.entrySet()) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return text.toString();
    }

    public static synchronized void setListener(Runnable value) {
        listener = value;
    }

    private StatusBus() {}
}
