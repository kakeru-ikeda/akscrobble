import com.akscrobble.core.Const;
import com.akscrobble.core.NowPlaying;
import com.akscrobble.core.SourceStatus;
import com.akscrobble.core.Sources;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SourcesTests {
    static void run() {
        TestKit.section("Sources");
        TestKit.eq("Qobuz preference key", "source_enabled_com.qobuz.music",
                Sources.prefKey(Const.PKG_QOBUZ));
        TestKit.eq("Apple status key", "apple", Sources.statusKey(Const.PKG_APPLE));
        TestKit.eq("AK Music status key", "akmusic", Sources.statusKey(Const.PKG_AKMUSIC));
        TestKit.eq("Qobuz status key", "qobuz", Sources.statusKey(Const.PKG_QOBUZ));
        TestKit.eq("unknown status key", "other.player", Sources.statusKey("other.player"));
        TestKit.check("display order follows UI order",
                Arrays.equals(new String[] {Const.PKG_AKMUSIC, Const.PKG_APPLE, Const.PKG_QOBUZ},
                        Sources.displayOrder()));
        TestKit.check("source labels are non-empty", !Sources.label(Const.PKG_AKMUSIC).isEmpty()
                && !Sources.label(Const.PKG_APPLE).isEmpty()
                && !Sources.label(Const.PKG_QOBUZ).isEmpty());
        TestKit.check("source tags are non-empty", !Sources.tag(Const.PKG_AKMUSIC).isEmpty()
                && !Sources.tag(Const.PKG_APPLE).isEmpty()
                && !Sources.tag(Const.PKG_QOBUZ).isEmpty());
        TestKit.eq("Qobuz service", Const.SVC_QOBUZ, Sources.serviceOf(Const.PKG_QOBUZ));
        TestKit.eq("unknown service", null, Sources.serviceOf("other.player"));

        Map<String, Boolean> enabled = new HashMap<String, Boolean>();
        Sources.Plan plan = Sources.reconcile(new ArrayList<String>(), enabled);
        TestKit.eq("empty active starts targets in TARGETS order",
                Arrays.asList(Const.PKG_APPLE, Const.PKG_AKMUSIC, Const.PKG_QOBUZ), plan.start);
        TestKit.eq("empty active stops none", new ArrayList<String>(), plan.stop);

        Collection<String> activeApple = Arrays.asList(Const.PKG_APPLE);
        enabled.put(Const.PKG_APPLE, Boolean.FALSE);
        plan = Sources.reconcile(activeApple, enabled);
        TestKit.eq("disabled active Apple stops", Arrays.asList(Const.PKG_APPLE), plan.stop);
        TestKit.check("disabled Apple is not started", !plan.start.contains(Const.PKG_APPLE));
        enabled.clear();
        enabled.put(Const.PKG_AKMUSIC, Boolean.FALSE);
        plan = Sources.reconcile(new ArrayList<String>(), enabled);
        TestKit.check("explicitly disabled source is not started", !plan.start.contains(Const.PKG_AKMUSIC));
        Collection<String> unknownActive = Arrays.asList("other.player");
        plan = Sources.reconcile(unknownActive, enabled);
        TestKit.eq("active unknown source stops", Arrays.asList("other.player"), plan.stop);

        TestKit.eq("service stopped has priority", SourceStatus.STOPPED,
                SourceStatus.of(false, false, false, "connected"));
        TestKit.eq("not installed has priority", SourceStatus.NOT_INSTALLED,
                SourceStatus.of(true, true, false, "connected"));
        TestKit.eq("disabled has priority", SourceStatus.DISABLED,
                SourceStatus.of(true, false, true, "connected"));
        TestKit.eq("connected state", SourceStatus.CONNECTED,
                SourceStatus.of(true, true, true, "connected"));
        TestKit.eq("connecting state", SourceStatus.CONNECTING,
                SourceStatus.of(true, true, true, "connecting"));
        TestKit.eq("absent state", SourceStatus.NOT_INSTALLED,
                SourceStatus.of(true, true, true, "absent"));
        TestKit.eq("disabled link state", SourceStatus.DISCONNECTED,
                SourceStatus.of(true, true, true, "disabled"));
        TestKit.eq("failed link state", SourceStatus.DISCONNECTED,
                SourceStatus.of(true, true, true, "failed"));
        TestKit.eq("suspended link state", SourceStatus.DISCONNECTED,
                SourceStatus.of(true, true, true, "suspended"));
        TestKit.eq("null link state", SourceStatus.DISCONNECTED,
                SourceStatus.of(true, true, true, null));

        Map<String, String> status = new HashMap<String, String>();
        status.put("playback_apple", String.valueOf(Const.STATE_PLAYING));
        status.put("np_title_akmusic", "AK track");
        TestKit.eq("playing source is preferred", Const.PKG_APPLE,
                NowPlaying.pick(new String[] {Const.PKG_AKMUSIC, Const.PKG_APPLE}, status));
        status.remove("playback_apple");
        TestKit.eq("first source with title is fallback", Const.PKG_AKMUSIC,
                NowPlaying.pick(new String[] {Const.PKG_AKMUSIC, Const.PKG_APPLE}, status));
        status.remove("np_title_akmusic");
        TestKit.eq("no track returns null", null,
                NowPlaying.pick(new String[] {Const.PKG_AKMUSIC, Const.PKG_APPLE}, status));
    }

    private SourcesTests() {}
}
