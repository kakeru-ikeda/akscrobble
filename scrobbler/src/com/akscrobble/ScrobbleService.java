package com.akscrobble;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import com.akscrobble.core.Const;
import com.akscrobble.core.Credentials;
import com.akscrobble.core.AlbumInfo;
import com.akscrobble.core.PendingScrobble;
import com.akscrobble.core.PlayTracker;
import com.akscrobble.core.Sanitizer;
import com.akscrobble.core.Strings;
import com.akscrobble.core.TrackInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ScrobbleService extends Service implements PlayerLink.Listener {
    private static final long NOTIFICATION_INTERVAL_MS = 5000L;

    private final Handler handler = new Handler();
    private final List<PlayerLink> playerLinks = new ArrayList<PlayerLink>();
    private final Map<String, PlayTracker> trackers =
            new LinkedHashMap<String, PlayTracker>();
    private volatile Sanitizer sanitizer;
    private AlbumResolver albumResolver;
    private ScrobbleDb database;
    private Sender sender;
    private long lastNotificationElapsed;
    private long lastPeriodicDrainElapsed;

    private final Runnable supervisor = new Runnable() {
        @Override
        public void run() {
            try {
                sanitizer = SanitizeStore.sanitizer(ScrobbleService.this);
                long now = SystemClock.elapsedRealtime();
                long uptime = Math.max(0L, now - StatusBus.serviceStartedElapsed);
                StatusBus.put("service", "running");
                StatusBus.put("uptime_ms", Long.toString(uptime));
                for (PlayerLink link : playerLinks) {
                    link.tick(now);
                }
                long wall = System.currentTimeMillis();
                for (Map.Entry<String, PlayTracker> entry : trackers.entrySet()) {
                    PlayTracker tracker = entry.getValue();
                    tracker.tick(wall, now);
                    publishTrackerStatus(entry.getKey(), tracker, now);
                }
                StatusBus.put("pending", Integer.toString(database.countPending()));
                sender.tick();
                if (now - lastPeriodicDrainElapsed >= 5L * 60L * 1000L) {
                    sender.requestDrain("periodic");
                    lastPeriodicDrainElapsed = now;
                }
                if (now - lastNotificationElapsed >= NOTIFICATION_INTERVAL_MS) {
                    NotificationManager manager = (NotificationManager)
                            getSystemService(Context.NOTIFICATION_SERVICE);
                    manager.notify(Const.NOTIF_ID,
                            Notifications.build(ScrobbleService.this,
                                    Ui.s(ScrobbleService.this, Strings.NOTIF_TITLE),
                                    new Prefs(ScrobbleService.this).getBoolean(Prefs.AUTH_INVALID)
                                            ? Ui.s(ScrobbleService.this, Strings.NOTIF_REAUTH)
                                            : Ui.s(ScrobbleService.this, Strings.NOTIF_RUNNING)));
                    lastNotificationElapsed = now;
                }
            } catch (Throwable error) {
                Log.e(Const.TAG, "Supervisor tick failed", error);
            } finally {
                handler.postDelayed(this, Const.TICK_INTERVAL_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(Const.NOTIF_ID, Notifications.build(this,
                Ui.s(this, Strings.NOTIF_TITLE), Ui.s(this, Strings.NOTIF_STARTING)));
        StatusBus.serviceStartedElapsed = SystemClock.elapsedRealtime();
        StatusBus.put("service", "running");
        lastNotificationElapsed = StatusBus.serviceStartedElapsed;
        sanitizer = SanitizeStore.sanitizer(this);
        albumResolver = new AlbumResolver(this);
        database = new ScrobbleDb(this);
        sender = new Sender(this, database, handler);
        lastPeriodicDrainElapsed = StatusBus.serviceStartedElapsed;
        for (String[] target : Const.TARGETS) {
            trackers.put(target[0], new PlayTracker(target[0], new TrackerSink()));
            PlayerLink link = new PlayerLink(this, target[0], target[1], handler, this);
            playerLinks.add(link);
            link.start();
        }
        handler.post(supervisor);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && Const.ACTION_STOP.equals(intent.getAction())) {
            new Prefs(this).putBoolean(Prefs.SERVICE_ENABLED, false);
            stopSelf();
        } else if (intent != null && Const.ACTION_FLUSH.equals(intent.getAction())) {
            sender.requestDrain("manual");
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        for (PlayerLink link : playerLinks) {
            link.stop();
        }
        playerLinks.clear();
        trackers.clear();
        if (sender != null) sender.close();
        if (database != null) database.close();
        handler.removeCallbacksAndMessages(null);
        StatusBus.put("service", "stopped");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onMetadata(String pkg, TrackInfo track) {
        Log.i(Const.TAG, pkg + " metadata title=" + track.title + " artist=" + track.artist);
        PlayTracker tracker = trackers.get(pkg);
        if (tracker != null) {
            long elapsed = SystemClock.elapsedRealtime();
            tracker.onMetadata(track, System.currentTimeMillis(), elapsed);
            // アルバム名を解決してから status を出す。ここで生の track を渡すと、
            // 直前に tracker.onMetadata -> onNowPlaying が発行した解決済み status を
            // 未解決の値で上書きしてしまい、AK Music の album が UI 上ずっと null に見える
            // （送信時は別途 enrichAkAlbum を通るので送信データ自体は正しかった）。
            publishTrackStatus(pkg, enrichAkAlbum(track));
            publishTrackerStatus(pkg, tracker, elapsed);
        }
    }

    @Override
    public void onState(String pkg, int state, long positionMs, float speed) {
        Log.i(Const.TAG, pkg + " playback state=" + state + " position=" + positionMs
                + " speed=" + speed);
        StatusBus.put("playback_" + statusKey(pkg), Integer.toString(state));
        PlayTracker tracker = trackers.get(pkg);
        if (tracker != null) {
            long elapsed = SystemClock.elapsedRealtime();
            tracker.onPlaybackState(state, positionMs, speed,
                    System.currentTimeMillis(), elapsed);
            publishTrackerStatus(pkg, tracker, elapsed);
        }
    }

    @Override
    public void onLinkState(String pkg, String state) {
        Log.i(Const.TAG, pkg + " link=" + state);
        StatusBus.put("link_" + statusKey(pkg), state);
        if ("suspended".equals(state) || "failed".equals(state)) {
            PlayTracker tracker = trackers.get(pkg);
            if (tracker != null) {
                long elapsed = SystemClock.elapsedRealtime();
                tracker.onDisconnected(System.currentTimeMillis(), elapsed);
                publishTrackerStatus(pkg, tracker, elapsed);
            }
        }
    }

    private TrackInfo enrichAkAlbum(TrackInfo track) {
        if (!Const.PKG_AKMUSIC.equals(track.sourcePkg)
                || !isBlank(track.album) || track.mediaId <= 0L) return track;
        AlbumInfo info = albumResolver.resolve(track.mediaId);
        if (info == null) return track;
        return track.withAlbum(info.album, info.albumArtist, info.trackNumber);
    }

    private void publishTrackStatus(String pkg, TrackInfo raw) {
        TrackInfo sanitized = sanitizer.apply(raw);
        String key = statusKey(pkg);
        StatusBus.put("track_raw_" + key, describe(raw));
        StatusBus.put("track_sanitized_" + key, describe(sanitized));
        Log.i(Const.TAG, pkg + " raw: " + describe(raw));
        Log.i(Const.TAG, pkg + " sanitized: " + describe(sanitized));
    }

    private void publishTrackerStatus(String pkg, PlayTracker tracker, long elapsed) {
        StatusBus.put("progress_" + statusKey(pkg),
                tracker.accumulatedMs(elapsed) + "/" + tracker.thresholdMs());
    }

    private final class TrackerSink implements PlayTracker.Sink {
        @Override public void onNowPlaying(TrackInfo track) {
            final TrackInfo enriched = enrichAkAlbum(track);
            final Sanitizer currentSanitizer = sanitizer;
            final TrackInfo outgoing = currentSanitizer.apply(enriched);
            publishTrackStatus(track.sourcePkg, enriched);
            NetThread.post(new Runnable() {
                @Override public void run() {
                    Credentials creds = ConfigStore.credentials(ScrobbleService.this);
                    new LastfmClient(creds.apiKey, creds.apiSecret, creds.sessionKey)
                            .updateNowPlaying(outgoing);
                }
            });
        }

        @Override public void onScrobble(TrackInfo track, long startedAtSec, long playedMs) {
            TrackInfo raw = enrichAkAlbum(track);
            PendingScrobble pending = new PendingScrobble(0L, raw.sourcePkg,
                    raw.artist, raw.title, raw.album, raw.albumArtist, raw.durationMs,
                    raw.trackNumber, startedAtSec, 0, "pending", null);
            database.enqueue(pending, System.currentTimeMillis() / 1000L);
            sender.requestDrain("enqueue");
            StatusBus.put("pending", Integer.toString(database.countPending()));
            publishTrackStatus(track.sourcePkg, raw);
        }
    }

    private static String describe(TrackInfo track) {
        return String.valueOf(track.artist) + " - " + String.valueOf(track.title)
                + " | album=" + String.valueOf(track.album)
                + " | albumArtist=" + String.valueOf(track.albumArtist);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }

    private static String statusKey(String pkg) {
        if (Const.PKG_APPLE.equals(pkg)) return "apple";
        if (Const.PKG_AKMUSIC.equals(pkg)) return "akmusic";
        return pkg;
    }
}
