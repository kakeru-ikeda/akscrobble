import com.akscrobble.core.Const;
import com.akscrobble.core.PlayTracker;
import com.akscrobble.core.TrackInfo;

import java.util.ArrayList;
import java.util.List;

public final class PlayTrackerTests {
    static void run() {
        TestKit.section("PlayTracker");
        trackInfo();
        thresholds();
        duplicateMetadata();
        pauseAndResume();
        finalization();
        repeatDetection();
        shortTrack();
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
