package com.akscrobble;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.akscrobble.core.Const;
import com.akscrobble.core.TrackInfo;

/** Maintains one permission-free MediaBrowser connection to a player. */
public final class PlayerLink {
    public interface Listener {
        void onMetadata(String pkg, TrackInfo track);
        void onState(String pkg, int state, long positionMs, float speed);
        void onLinkState(String pkg, String state);
    }

    private static final long ABSENT_RETRY_MS = 10L * 60L * 1000L;
    private static final long CONNECT_TIMEOUT_MS = 30L * 1000L;
    private static final long[] RETRY_MS = {5000L, 15000L, 60000L, 300000L};

    private final Context context;
    private final String pkg;
    private final ComponentName component;
    private final Handler main;
    private final Listener listener;

    private volatile String state = "failed";
    private boolean started;
    private int retryIndex;
    private long connectStartedElapsed;
    private long nextAttemptAtElapsed;
    private MediaBrowser browser;
    private MediaController controller;

    private final MediaController.Callback controllerCallback =
            new MediaController.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadata metadata) {
                    publishMetadata(metadata);
                }

                @Override
                public void onPlaybackStateChanged(PlaybackState playback) {
                    publishPlayback(playback);
                }
            };

    public PlayerLink(Context ctx, String pkg, String service, Handler main, Listener listener) {
        this.context = ctx.getApplicationContext();
        this.pkg = pkg;
        this.component = new ComponentName(pkg, service);
        this.main = main;
        this.listener = listener;
    }

    public void start() {
        runOnMain(new Runnable() {
            @Override public void run() {
                if (started) return;
                started = true;
                retryIndex = 0;
                nextAttemptAtElapsed = 0L;
                attemptConnect(SystemClock.elapsedRealtime());
            }
        });
    }

    public void stop() {
        runOnMain(new Runnable() {
            @Override public void run() {
                started = false;
                cleanup();
            }
        });
    }

    public void tick(final long elapsedMs) {
        runOnMain(new Runnable() {
            @Override public void run() {
                if (!started) return;
                if ("connecting".equals(state)
                        && elapsedMs - connectStartedElapsed >= CONNECT_TIMEOUT_MS) {
                    fail("connection timed out", elapsedMs, "failed");
                } else if (!"connected".equals(state) && !"connecting".equals(state)
                        && elapsedMs >= nextAttemptAtElapsed) {
                    attemptConnect(elapsedMs);
                }
            }
        });
    }

    public String state() {
        return state;
    }

    private void runOnMain(Runnable action) {
        if (Looper.myLooper() == main.getLooper()) action.run();
        else main.post(action);
    }

    private void attemptConnect(final long elapsedMs) {
        if (!started || "connecting".equals(state) || "connected".equals(state)) return;
        cleanup();
        try {
            context.getPackageManager().getPackageInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException missing) {
            nextAttemptAtElapsed = elapsedMs + ABSENT_RETRY_MS;
            setState("absent");
            return;
        } catch (Throwable error) {
            fail("package check failed", elapsedMs, "failed", error);
            return;
        }

        final MediaBrowser[] reference = new MediaBrowser[1];
        try {
            reference[0] = new MediaBrowser(context, component,
                    new MediaBrowser.ConnectionCallback() {
                        @Override public void onConnected() {
                            if (!started || browser != reference[0]) return;
                            try {
                                controller = new MediaController(
                                        context, browser.getSessionToken());
                                controller.registerCallback(controllerCallback, main);
                                retryIndex = 0;
                                nextAttemptAtElapsed = 0L;
                                setState("connected");
                                publishMetadata(controller.getMetadata());
                                publishPlayback(controller.getPlaybackState());
                            } catch (Throwable error) {
                                fail("controller setup failed",
                                        SystemClock.elapsedRealtime(), "failed", error);
                            }
                        }

                        @Override public void onConnectionSuspended() {
                            if (browser == reference[0]) {
                                fail("connection suspended", SystemClock.elapsedRealtime(),
                                        "suspended");
                            }
                        }

                        @Override public void onConnectionFailed() {
                            if (browser == reference[0]) {
                                fail("connection failed", SystemClock.elapsedRealtime(), "failed");
                            }
                        }
                    }, null);
            browser = reference[0];
            connectStartedElapsed = elapsedMs;
            setState("connecting");
            browser.connect();
        } catch (Throwable error) {
            fail("connect threw", elapsedMs, "failed", error);
        }
    }

    private void fail(String message, long elapsedMs, String failedState) {
        Log.w(Const.TAG, pkg + ": " + message);
        cleanup();
        scheduleRetry(elapsedMs);
        setState(failedState);
    }

    private void fail(String message, long elapsedMs, String failedState, Throwable error) {
        Log.w(Const.TAG, pkg + ": " + message, error);
        cleanup();
        scheduleRetry(elapsedMs);
        setState(failedState);
    }

    private void scheduleRetry(long elapsedMs) {
        int index = Math.min(retryIndex, RETRY_MS.length - 1);
        nextAttemptAtElapsed = elapsedMs + RETRY_MS[index];
        if (retryIndex < RETRY_MS.length - 1) retryIndex++;
    }

    private void cleanup() {
        if (controller != null) {
            try { controller.unregisterCallback(controllerCallback); }
            catch (Throwable ignored) { }
            controller = null;
        }
        if (browser != null) {
            try { browser.disconnect(); }
            catch (Throwable ignored) { }
            browser = null;
        }
    }

    private void setState(String value) {
        state = value;
        listener.onLinkState(pkg, value);
    }

    private void publishMetadata(MediaMetadata metadata) {
        if (metadata == null) return;
        long mediaId = -1L;
        String rawMediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        if (rawMediaId != null) {
            try { mediaId = Long.parseLong(rawMediaId); }
            catch (NumberFormatException ignored) { }
        }
        listener.onMetadata(pkg, new TrackInfo(pkg,
                metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
                metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                metadata.getLong(MediaMetadata.METADATA_KEY_DURATION), mediaId, 0));
    }

    private void publishPlayback(PlaybackState playback) {
        if (playback == null) return;
        listener.onState(pkg, playback.getState(), playback.getPosition(),
                playback.getPlaybackSpeed());
    }
}
