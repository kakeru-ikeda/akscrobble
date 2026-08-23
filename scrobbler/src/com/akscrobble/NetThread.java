package com.akscrobble;

import android.os.Handler;
import android.os.HandlerThread;

/** Single, lazily-created worker thread for network and other blocking I/O. */
public final class NetThread {
    private static final class Holder {
        private static final NetThread INSTANCE = new NetThread();
    }

    private final HandlerThread thread;
    private final Handler handler;

    private NetThread() {
        thread = new HandlerThread("aks-net");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    public static void post(Runnable runnable) {
        Holder.INSTANCE.handler.post(runnable);
    }

    public static void postDelayed(Runnable runnable, long delayMs) {
        Holder.INSTANCE.handler.postDelayed(runnable, delayMs);
    }
}
