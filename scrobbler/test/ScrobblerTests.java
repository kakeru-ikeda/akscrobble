import com.akscrobble.core.Const;

public final class ScrobblerTests {
    public static void main(String[] args) {
        TestKit.section("Const");
        TestKit.check("API root uses HTTPS", Const.API_ROOT.startsWith("https://"));
        TestKit.eq("three player targets", Integer.valueOf(3), Integer.valueOf(Const.TARGETS.length));

        boolean containsAmazon = false;
        boolean containsQobuz = false;
        for (String[] target : Const.TARGETS) {
            if ("com.amazon.mp3".equals(target[0])) {
                containsAmazon = true;
            }
            if ("com.qobuz.music".equals(target[0])
                    && "com.qobuz.android.mobile.component.mediaplayer.MusicService"
                            .equals(target[1])) {
                containsQobuz = true;
            }
        }
        TestKit.check("Amazon Music remains unsupported", !containsAmazon);
        TestKit.check("Qobuz target present", containsQobuz);
        TestKit.eq("Qobuz package constant", "com.qobuz.music", Const.PKG_QOBUZ);
        TestKit.eq("scrobble cap", Long.valueOf(240000L), Long.valueOf(Const.SCROBBLE_CAP_MS));
        StringsTests.run();
        SanitizerTests.run();
        SanitizeConfigTests.run();
        SanitizeConfigTests.runEscaping();
        ComponentFilterTests.run();
        AlbumInfoTests.run();
        PlayTrackerTests.run();
        LastfmTests.run();
        QueuePolicyTests.run();
        BackoffTests.run();
        ConfigTests.run();
        UpdateManifestTests.run();
        UpdateDecisionTests.run();
        Sha256SinkTests.run();
        System.exit(TestKit.finish());
    }

    private ScrobblerTests() {}
}
