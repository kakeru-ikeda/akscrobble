package com.akscrobble.core;

public final class Const {
    public static final String TAG = "AKSCROB";
    public static final String API_ROOT = "https://ws.audioscrobbler.com/2.0/";
    public static final String USER_AGENT = "AKScrobble/1.0 (SP2000T; Android 9)";

    public static final String PKG_APPLE = "com.apple." + "android" + ".music";
    public static final String SVC_APPLE =
            PKG_APPLE + ".player.MediaPlaybackService";
    public static final String PKG_AKMUSIC = "com.iriver.ak.music";
    public static final String SVC_AKMUSIC =
            "com.iriver.ak.music.player.service.MediaPlaybackService";
    public static final String PKG_QOBUZ = "com.qobuz.music";
    public static final String SVC_QOBUZ =
            "com.qobuz." + "android" + ".mobile.component.mediaplayer.MusicService";
    public static final String[][] TARGETS = {
        {PKG_APPLE, SVC_APPLE},
        {PKG_AKMUSIC, SVC_AKMUSIC},
        {PKG_QOBUZ, SVC_QOBUZ}
    };

    public static final String CONFIG_DIR = "AKScrobble";
    public static final String CONFIG_FILE = "config.properties";
    public static final String NOTIF_CHANNEL = "akscrobble";
    public static final int NOTIF_ID = 1;
    public static final String ACTION_START = "com.akscrobble.action.START";
    public static final String ACTION_STOP = "com.akscrobble.action.STOP";
    public static final String ACTION_FLUSH = "com.akscrobble.action.FLUSH";

    public static final long SCROBBLE_MIN_DURATION_MS = 30000L;
    public static final long SCROBBLE_CAP_MS = 240000L;
    public static final long UNKNOWN_DURATION_THRESHOLD_MS = 240000L;
    public static final long RESTART_POSITION_MS = 3000L;
    public static final long TICK_INTERVAL_MS = 15000L;

    public static final int STATE_NONE = 0;
    public static final int STATE_STOPPED = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_PLAYING = 3;

    private Const() {}
}
