import com.akscrobble.core.Const;
import com.akscrobble.core.PlayTracker;
import com.akscrobble.core.TrackInfo;

import java.util.ArrayList;
import java.util.List;

public final class PlayTrackerTests {
    private static final int STATE_BUFFERING = 6;

    static void run() {
        TestKit.section("PlayTracker");
        trackInfo();
        thresholds();
        duplicateMetadata();
        pauseAndResume();
        finalization();
        repeatDetection();
        shortTrack();
        qobuzSequences();
    }

    private static void trackInfo() {
        TrackInfo raw = track(" Title ", "Artist", null, 216000L);
        TestKit.check("TrackInfo usable", raw.isUsable());
        TestKit.check("blank title unusable", !track("  ", "Artist", null, 1L).isUsable());
        TestKit.eq("key excludes album", raw.key(),
                raw.withAlbum("Later album", "Album Artist", 4).key());
        TestKit.check("withAlbum is immutable", raw.album == null);
        TestKit.check("all fields participate in equality",
                !raw.equals(raw.withAlbum("Later album", "Album Artist", 4)));
        TestKit.eq("equal values have equal hash", Integer.valueOf(raw.hashCode()),
                Integer.valueOf(raw.withText(raw.title, raw.artist, raw.album, raw.albumArtist).hashCode()));
    }

    private static void thresholds() {
        TestKit.eq("216 second threshold", Long.valueOf(108000L),
                Long.valueOf(trackerFor(216000L).thresholdMs()));
        TestKit.eq("ten minute threshold capped", Long.valueOf(240000L),
                Long.valueOf(trackerFor(600000L).thresholdMs()));
        TestKit.eq("short track never scrobbles", Long.valueOf(Long.MAX_VALUE),
                Long.valueOf(trackerFor(29999L).thresholdMs()));
        TestKit.eq("unknown duration threshold", Long.valueOf(240000L),
                Long.valueOf(trackerFor(0L).thresholdMs()));
    }

    private static void duplicateMetadata() {
        Sink sink = new Sink();
        PlayTracker tracker = new PlayTracker("player", sink);
        TrackInfo first = track("Song", "Artist", null, 120000L);
        tracker.onPlaybackState(Const.STATE_PLAYING, 10000L, 1F, 100000L, 1000L);
        tracker.onMetadata(first, 101000L, 2000L);
        tracker.onMetadata(first, 120000L, 21000L);
        tracker.onMetadata(first.withAlbum("Resolved", "Artist", 2), 130000L, 31000L);
        tracker.tick(161000L, 62000L);
        tracker.tick(200000L, 101000L);
        TestKit.eq("duplicate metadata scrobbles once", Integer.valueOf(1),
                Integer.valueOf(sink.scrobbles.size()));
        TestKit.eq("duplicate keeps first start timestamp", Long.valueOf(101L),
                Long.valueOf(sink.scrobbles.get(0).startedAt));
        TestKit.eq("metadata enrichment replaces current album", "Resolved", tracker.current().album);
    }

    private static void pauseAndResume() {
        Sink sink = new Sink();
        PlayTracker tracker = new PlayTracker("player", sink);
        tracker.onMetadata(track("Long", "Artist", null, 600000L), 0L, 0L);
        tracker.onPlaybackState(Const.STATE_PLAYING, -1L, 1F, 0L, 0L);
        tracker.onPlaybackState(Const.STATE_PAUSED, -1L, 0F, 60000L, 60000L);
        tracker.tick(90000L, 90000L);
        tracker.tick(120000L, 120000L);
        TestKit.eq("paused ticks do not accumulate", Long.valueOf(60000L),
                Long.valueOf(tracker.accumulatedMs(120000L)));
        tracker.onPlaybackState(Const.STATE_PLAYING, -1L, 1F, 120000L, 120000L);
        tracker.onPlaybackState(Const.STATE_PAUSED, -1L, 0F, 180000L, 180000L);
        TestKit.eq("play pause resume accumulates", Long.valueOf(120000L),
                Long.valueOf(tracker.accumulatedMs(180000L)));
        TestKit.eq("now playing sent on start and resume", Integer.valueOf(2),
                Integer.valueOf(sink.nowPlaying));
    }

    private static void finalization() {
        TestKit.eq("track change finalizes reached track", Integer.valueOf(1),
                Integer.valueOf(finalizeByChange(60000L).scrobbles.size()));
        TestKit.eq("track change drops unreached track", Integer.valueOf(0),
                Integer.valueOf(finalizeByChange(59000L).scrobbles.size()));

        Sink stopped = new Sink();
        PlayTracker stopTracker = playing(stopped, 120000L);
        stopTracker.onPlaybackState(Const.STATE_STOPPED, -1L, 0F, 60000L, 60000L);
        TestKit.eq("stopped finalizes", Integer.valueOf(1), Integer.valueOf(stopped.scrobbles.size()));
        TestKit.eq("stopped clears current", null, stopTracker.current());

        Sink disconnected = new Sink();
        PlayTracker disconnectTracker = playing(disconnected, 120000L);
        disconnectTracker.onDisconnected(60000L, 60000L);
        TestKit.eq("disconnect finalizes", Integer.valueOf(1),
                Integer.valueOf(disconnected.scrobbles.size()));

        Sink once = new Sink();
        PlayTracker onceTracker = playing(once, 120000L);
        onceTracker.tick(60000L, 60000L);
        onceTracker.tick(180000L, 180000L);
        TestKit.eq("threshold callback happens once", Integer.valueOf(1),
                Integer.valueOf(once.scrobbles.size()));
    }

    private static void repeatDetection() {
        Sink repeat = new Sink();
        PlayTracker tracker = playing(repeat, 120000L);
        tracker.tick(60000L, 60000L);
        tracker.onPlaybackState(Const.STATE_PLAYING, 500L, 1F, 61000L, 61000L);
        tracker.tick(121000L, 121000L);
        TestKit.eq("known low position detects repeat", Integer.valueOf(2),
                Integer.valueOf(repeat.scrobbles.size()));

        Sink unknown = new Sink();
        PlayTracker unknownTracker = playing(unknown, 120000L);
        unknownTracker.tick(60000L, 60000L);
        unknownTracker.onPlaybackState(Const.STATE_PLAYING, -1L, 1F, 61000L, 61000L);
        unknownTracker.tick(121000L, 121000L);
        TestKit.eq("negative position does not detect repeat", Integer.valueOf(1),
                Integer.valueOf(unknown.scrobbles.size()));
        TestKit.eq("negative positions preserve accumulation", Long.valueOf(121000L),
                Long.valueOf(unknownTracker.accumulatedMs(121000L)));

        // 実機の Apple Music は再生中に position=0 の PlaybackState を再送してくる
        // （曲送り直後に複数回観測）。閾値未達でこれをリピートと誤検知すると
        // 同じ曲が二重 scrobble されるため、明示的に守る。
        Sink early = new Sink();
        PlayTracker earlyTracker = playing(early, 120000L);
        earlyTracker.onPlaybackState(Const.STATE_PLAYING, 0L, 1F, 2000L, 2000L);
        earlyTracker.onPlaybackState(Const.STATE_PLAYING, 0L, 1F, 4000L, 4000L);
        earlyTracker.tick(121000L, 121000L);
        TestKit.eq("low position below threshold is not a repeat", Integer.valueOf(1),
                Integer.valueOf(early.scrobbles.size()));
    }

    private static void shortTrack() {
        Sink sink = new Sink();
        PlayTracker tracker = playing(sink, 29999L);
        tracker.tick(600000L, 600000L);
        tracker.onPlaybackState(Const.STATE_STOPPED, -1L, 0F, 600000L, 600000L);
        TestKit.eq("under-30-second track never scrobbles", Integer.valueOf(0),
                Integer.valueOf(sink.scrobbles.size()));
    }

    private static void qobuzSequences() {
        TrackInfo t1 = qobuzTrack("Song 1");
        TrackInfo t2 = qobuzTrack("Song 2");

        Sink gapless = new Sink();
        PlayTracker gaplessTracker = new PlayTracker("com.qobuz.music", gapless);
        gaplessTracker.onPlaybackState(Const.STATE_PLAYING, 0L, 1F, 0L, 0L);
        gaplessTracker.onMetadata(t1, 0L, 0L);
        gaplessTracker.tick(114000L, 114000L);
        gaplessTracker.onMetadata(t2, 228000L, 228000L);
        gaplessTracker.onMetadata(t2, 228100L, 228100L);
        gaplessTracker.onPlaybackState(STATE_BUFFERING, 0L, 0F, 228200L, 228200L);
        gaplessTracker.onPlaybackState(Const.STATE_PLAYING, 50L, 1F, 229000L, 229000L);
        TestKit.eq("qobuz gapless keeps one scrobble per track", Integer.valueOf(1),
                Integer.valueOf(gapless.scrobbles.size()));
        TestKit.eq("qobuz gapless metadata selects next track", "Song 2",
                gaplessTracker.current().title);
        TestKit.eq("qobuz gapless excludes buffering time", Long.valueOf(10200L),
                Long.valueOf(gaplessTracker.accumulatedMs(239000L)));
        gaplessTracker.tick(342800L, 342800L);
        TestKit.eq("qobuz gapless scrobbles next track", Integer.valueOf(2),
                Integer.valueOf(gapless.scrobbles.size()));

        Sink stateFirst = new Sink();
        PlayTracker stateFirstTracker = new PlayTracker("com.qobuz.music", stateFirst);
        stateFirstTracker.onPlaybackState(Const.STATE_PLAYING, 0L, 1F, 0L, 0L);
        stateFirstTracker.onMetadata(t1, 0L, 0L);
        stateFirstTracker.tick(114000L, 114000L);
        stateFirstTracker.onPlaybackState(STATE_BUFFERING, 0L, 0F, 228000L, 228000L);
        stateFirstTracker.onPlaybackState(Const.STATE_PLAYING, 0L, 1F, 228500L, 228500L);
        stateFirstTracker.onMetadata(t2, 228600L, 228600L);
        stateFirstTracker.tick(342600L, 342600L);
        stateFirstTracker.tick(400000L, 400000L);
        TestKit.eq("qobuz state-first track change scrobbles each track once", Integer.valueOf(2),
                Integer.valueOf(stateFirst.scrobbles.size()));
        TestKit.eq("qobuz state-first metadata selects next track", "Song 2",
                stateFirstTracker.current().title);

        Sink repeat = new Sink();
        PlayTracker repeatTracker = new PlayTracker("com.qobuz.music", repeat);
        repeatTracker.onPlaybackState(Const.STATE_PLAYING, 0L, 1F, 0L, 0L);
        repeatTracker.onMetadata(t1, 0L, 0L);
        repeatTracker.tick(114000L, 114000L);
        repeatTracker.onPlaybackState(STATE_BUFFERING, 0L, 0F, 228000L, 228000L);
        repeatTracker.onPlaybackState(Const.STATE_PLAYING, 0L, 1F, 228500L, 228500L);
        repeatTracker.tick(342500L, 342500L);
        TestKit.eq("qobuz buffering repeat scrobbles second play", Integer.valueOf(2),
                Integer.valueOf(repeat.scrobbles.size()));
        TestKit.eq("qobuz buffering repeat accumulates second play", Long.valueOf(114000L),
                Long.valueOf(repeatTracker.accumulatedMs(342500L)));

        Sink pause = new Sink();
        PlayTracker pauseTracker = new PlayTracker("com.qobuz.music", pause);
        pauseTracker.onPlaybackState(Const.STATE_PLAYING, 0L, 1F, 0L, 0L);
        pauseTracker.onMetadata(t1, 0L, 0L);
        pauseTracker.tick(114000L, 114000L);
        pauseTracker.onPlaybackState(Const.STATE_PAUSED, 120000L, 0F, 120000L, 120000L);
        pauseTracker.onPlaybackState(Const.STATE_PLAYING, 0L, 1F, 130000L, 130000L);
        pauseTracker.tick(244000L, 244000L);
        TestKit.eq("qobuz paused repeat scrobbles second play", Integer.valueOf(2),
                Integer.valueOf(pause.scrobbles.size()));
    }

    private static TrackInfo qobuzTrack(String title) {
        return new TrackInfo("com.qobuz.music", title, "Artist", "Album", null,
                228000L, -1L, 0);
    }

    private static PlayTracker trackerFor(long durationMs) {
        PlayTracker tracker = new PlayTracker("player", new Sink());
        tracker.onMetadata(track("Song", "Artist", null, durationMs), 0L, 0L);
        return tracker;
    }

    private static PlayTracker playing(Sink sink, long durationMs) {
        PlayTracker tracker = new PlayTracker("player", sink);
        tracker.onMetadata(track("Song", "Artist", null, durationMs), 0L, 0L);
        tracker.onPlaybackState(Const.STATE_PLAYING, -1L, 1F, 0L, 0L);
        return tracker;
    }

    private static Sink finalizeByChange(long elapsedMs) {
        Sink sink = new Sink();
        PlayTracker tracker = playing(sink, 120000L);
        tracker.onMetadata(track("Next", "Artist", null, 120000L),
                elapsedMs, elapsedMs);
        return sink;
    }

    private static TrackInfo track(String title, String artist, String album, long durationMs) {
        return new TrackInfo("player", title, artist, album, null, durationMs, 7L, 0);
    }

    private static final class Event {
        final long startedAt;
        Event(long startedAt) { this.startedAt = startedAt; }
    }

    private static final class Sink implements PlayTracker.Sink {
        int nowPlaying;
        final List<Event> scrobbles = new ArrayList<Event>();

        @Override public void onNowPlaying(TrackInfo track) { nowPlaying++; }
        @Override public void onScrobble(TrackInfo track, long startedAt, long playedMs) {
            scrobbles.add(new Event(startedAt));
        }
    }

    private PlayTrackerTests() {}
}
