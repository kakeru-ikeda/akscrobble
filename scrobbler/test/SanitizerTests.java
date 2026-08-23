import com.akscrobble.core.SanitizeRule;
import com.akscrobble.core.Sanitizer;
import com.akscrobble.core.TrackInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SanitizerTests {
    static void run() {
        TestKit.section("Sanitizer");
        Sanitizer sanitizer = Sanitizer.defaults();

        TestKit.eq("Single suffix", "もっと高く",
                sanitizer.apply(SanitizeRule.Scope.ALBUM, "もっと高く - Single"));
        TestKit.eq("EP suffix", "Songs",
                sanitizer.apply(SanitizeRule.Scope.ALBUM, "Songs - EP"));
        TestKit.eq("EP within a word", "EPisode One",
                sanitizer.apply(SanitizeRule.Scope.ALBUM, "EPisode One"));
        TestKit.eq("Single within a word", "Singleton",
                sanitizer.apply(SanitizeRule.Scope.ALBUM, "Singleton"));
        TestKit.eq("album rule does not affect track", "Help - EP",
                sanitizer.apply(SanitizeRule.Scope.TRACK, "Help - EP"));
        // Scope.ALBUM は album にのみ効く。album_artist に波及すると
        // 「Various - EP」のようなアーティスト名を壊すため、分離を明示的に守る。
        TestKit.eq("album rule does not affect album_artist", "Various - EP",
                sanitizer.apply(SanitizeRule.Scope.ALBUM_ARTIST, "Various - EP"));
        TestKit.eq("album rule does not affect artist", "Single - Single",
                sanitizer.apply(SanitizeRule.Scope.ARTIST, "Single - Single"));
        TestKit.eq("empty-result safeguard", "- EP",
                sanitizer.apply(SanitizeRule.Scope.ALBUM, "- EP"));
        TestKit.eq("collapse and trim spaces", "A B",
                sanitizer.apply(SanitizeRule.Scope.ALL, "  A   B  "));
        TestKit.eq("zero-width character", "AB",
                sanitizer.apply(SanitizeRule.Scope.ALL, "A\u200BB"));
        TestKit.eq("deluxe is off by default", "X (Deluxe Edition)",
                sanitizer.apply(SanitizeRule.Scope.ALBUM, "X (Deluxe Edition)"));

        Set<String> disabled = defaultDisabled();
        disabled.remove("album-deluxe");
        Sanitizer withDeluxe = new Sanitizer(Sanitizer.DEFAULT_RULES, disabled, true);
        TestKit.eq("deluxe can be enabled", "X",
                withDeluxe.apply(SanitizeRule.Scope.ALBUM, "X (Deluxe Edition)"));

        List<String> explanation = sanitizer.explain(
                SanitizeRule.Scope.ALBUM, "Songs - EP");
        TestKit.eq("explain changed rule count", Integer.valueOf(1),
                Integer.valueOf(explanation.size()));
        TestKit.eq("explain EP change", "album-ep: 'Songs - EP' -> 'Songs'",
                explanation.get(0));

        TestKit.eq("null remains null", null,
                sanitizer.apply(SanitizeRule.Scope.ALBUM, null));
        Sanitizer off = new Sanitizer(Sanitizer.DEFAULT_RULES, new HashSet<String>(), false);
        TestKit.eq("global disable preserves raw value", "  Songs - EP  ",
                off.apply(SanitizeRule.Scope.ALBUM, "  Songs - EP  "));

        TrackInfo raw = new TrackInfo("player", "Title - Single", "Artist - Single",
                "Album - Single", "Album Artist - Single", 180000L, 42L, 3);
        TrackInfo sanitized = sanitizer.apply(raw);
        TestKit.eq("TrackInfo album sanitized", "Album", sanitized.album);
        TestKit.eq("TrackInfo title keeps album suffix", "Title - Single", sanitized.title);
        TestKit.eq("TrackInfo artist keeps album suffix", "Artist - Single", sanitized.artist);
        TestKit.eq("TrackInfo album artist keeps album suffix", "Album Artist - Single",
                sanitized.albumArtist);
        TestKit.eq("TrackInfo input remains raw", "Album - Single", raw.album);
        TestKit.check("TrackInfo sanitizer returns a new value", sanitized != raw);
    }

    private static Set<String> defaultDisabled() {
        Set<String> disabled = new HashSet<String>();
        for (SanitizeRule rule : Sanitizer.DEFAULT_RULES) {
            if (!rule.defaultOn) {
                disabled.add(rule.id);
            }
        }
        return disabled;
    }

    private SanitizerTests() {}
}
