package com.akscrobble.core;

public final class PlayTracker {
    public interface Sink {
        void onNowPlaying(TrackInfo track);
        void onScrobble(TrackInfo track, long startedAtUnixSec, long playedMs);
    }

    private final String sourcePkg;
    private final Sink sink;
    private TrackInfo current;
    private long startedAtSec;
    private long accumulatedMs;
    private long playingSinceElapsed = -1L;
    private boolean scrobbled;
    private boolean nowPlayingSent;
    private int lastState = Const.STATE_NONE;

    public PlayTracker(String sourcePkg, Sink sink) {
        if (sink == null) throw new IllegalArgumentException("sink must not be null");
        this.sourcePkg = sourcePkg;
        this.sink = sink;
    }

    public void onMetadata(TrackInfo track, long wallMs, long elapsedMs) {
        if (track == null || !track.isUsable()) return;
        if (current != null && current.key().equals(track.key())) {
            current = mergeEnrichedText(current, track);
            return;
        }
        finish(elapsedMs);
        start(track, wallMs, elapsedMs);
    }

    public void onPlaybackState(int state, long positionMs, float speed,
            long wallMs, long elapsedMs) {
        boolean playing = state == Const.STATE_PLAYING;

        if (playing && current != null && positionMs >= 0L
                && positionMs < Const.RESTART_POSITION_MS
                && accumulatedMs(elapsedMs) >= Math.max(
                        Const.RESTART_POSITION_MS, thresholdMs())) {
            TrackInfo repeated = current;
            finish(elapsedMs);
            // 直前が BUFFERING/PAUSED でも再生中として再開する。
            // start() は lastState で再生中かどうかを判断する。
            lastState = state;
            start(repeated, wallMs, elapsedMs);
        } else if (playing && playingSinceElapsed < 0L) {
            playingSinceElapsed = elapsedMs;
            sendNowPlaying();
        } else if (!playing && playingSinceElapsed >= 0L) {
            accumulateTo(elapsedMs);
            nowPlayingSent = false;
            maybeScrobble(accumulatedMs);
        }

        lastState = state;
        if (state == Const.STATE_STOPPED || state == Const.STATE_NONE) {
            finish(elapsedMs);
        }
    }

    public void tick(long wallMs, long elapsedMs) {
        maybeScrobble(accumulatedMs(elapsedMs));
    }

    public void onDisconnected(long wallMs, long elapsedMs) {
        finish(elapsedMs);
        lastState = Const.STATE_NONE;
    }

    public TrackInfo current() {
        return current;
    }

    public long accumulatedMs(long elapsedMs) {
        if (playingSinceElapsed < 0L) return accumulatedMs;
        return accumulatedMs + nonNegativeDelta(elapsedMs, playingSinceElapsed);
    }

    public long thresholdMs() {
        if (current == null) return Long.MAX_VALUE;
        long duration = current.durationMs;
        if (duration >= Const.SCROBBLE_MIN_DURATION_MS) {
            return Math.min(duration / 2L, Const.SCROBBLE_CAP_MS);
        }
        if (duration <= 0L) return Const.UNKNOWN_DURATION_THRESHOLD_MS;
        return Long.MAX_VALUE;
    }

    private void start(TrackInfo track, long wallMs, long elapsedMs) {
        current = track;
        startedAtSec = wallMs / 1000L;
        accumulatedMs = 0L;
        scrobbled = false;
        nowPlayingSent = false;
        playingSinceElapsed = lastState == Const.STATE_PLAYING ? elapsedMs : -1L;
        if (playingSinceElapsed >= 0L) sendNowPlaying();
    }

    private void finish(long elapsedMs) {
        if (current == null) return;
        accumulateTo(elapsedMs);
        maybeScrobble(accumulatedMs);
        current = null;
        playingSinceElapsed = -1L;
        nowPlayingSent = false;
    }

    private void accumulateTo(long elapsedMs) {
        if (playingSinceElapsed >= 0L) {
            accumulatedMs += nonNegativeDelta(elapsedMs, playingSinceElapsed);
            playingSinceElapsed = -1L;
        }
    }

    private void maybeScrobble(long playedMs) {
        if (current != null && !scrobbled && playedMs >= thresholdMs()) {
            scrobbled = true;
            sink.onScrobble(current, startedAtSec, playedMs);
        }
    }

    private void sendNowPlaying() {
        if (current != null && !nowPlayingSent) {
            nowPlayingSent = true;
            sink.onNowPlaying(current);
        }
    }

    private static TrackInfo mergeEnrichedText(TrackInfo oldTrack, TrackInfo newTrack) {
        String album = chooseEnrichment(oldTrack.album, newTrack.album);
        String albumArtist = chooseEnrichment(oldTrack.albumArtist, newTrack.albumArtist);
        if (eq(album, oldTrack.album) && eq(albumArtist, oldTrack.albumArtist)) return oldTrack;
        return oldTrack.withAlbum(album, albumArtist,
                newTrack.trackNumber != 0 ? newTrack.trackNumber : oldTrack.trackNumber);
    }

    private static String chooseEnrichment(String oldValue, String newValue) {
        return isBlank(oldValue) && !isBlank(newValue) ? newValue : oldValue;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }

    private static boolean eq(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private static long nonNegativeDelta(long end, long start) {
        return end > start ? end - start : 0L;
    }
}
