import com.akscrobble.core.Const;

public final class ScrobblerTests {
    public static void main(String[] args) {
        TestKit.section("Const");
        TestKit.check("API root uses HTTPS", Const.API_ROOT.startsWith("https://"));
        TestKit.eq("two player targets", Integer.valueOf(2), Integer.valueOf(Const.TARGETS.length));

        boolean containsAmazon = false;
        for (String[] target : Const.TARGETS) {
            if ("com.amazon.mp3".equals(target[0])) {
                containsAmazon = true;
            }
        }
        TestKit.check("Amazon Music remains unsupported", !containsAmazon);
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
        System.exit(TestKit.finish());
    }

    private ScrobblerTests() {}
}
