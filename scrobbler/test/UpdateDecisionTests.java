import com.akscrobble.core.update.UpdateDecision;
import com.akscrobble.core.update.UpdateManifest;

final class UpdateDecisionTests {
    private static final String PREFIX = "https://nyanstall.sukisuki.club/";
    private static final String SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    static void run() {
        TestKit.section("UpdateDecision");
        TestKit.eq("same version", UpdateDecision.NONE, decide(10, 10, PREFIX + "a.apk", false, null));
        TestKit.eq("lower version", UpdateDecision.NONE, decide(11, 10, PREFIX + "a.apk", false, null));
        TestKit.eq("higher version optional", UpdateDecision.OPTIONAL, decide(10, 11, PREFIX + "a.apk", false, null));
        TestKit.eq("explicit required", UpdateDecision.REQUIRED, decide(10, 11, PREFIX + "a.apk", true, null));
        TestKit.eq("minimum code required", UpdateDecision.REQUIRED, decide(10, 11, PREFIX + "a.apk", false, Long.valueOf(11)));
        TestKit.eq("http rejected", UpdateDecision.REJECTED, decide(10, 11, "http://nyanstall.sukisuki.club/a.apk", false, null));
        TestKit.eq("other host rejected", UpdateDecision.REJECTED, decide(10, 11, "https://example.com/a.apk", false, null));
        TestKit.eq("prefix spoof rejected", UpdateDecision.REJECTED, decide(10, 11, "https://nyanstall.sukisuki.club.evil.example/a.apk", false, null));
        TestKit.eq("dot dot rejected", UpdateDecision.REJECTED, decide(10, 11, PREFIX + "../a.apk", false, null));
        TestKit.eq("invalid old version remains none", UpdateDecision.NONE, decide(11, 10, "http://evil", false, null));
        TestKit.eq("space rejected", UpdateDecision.REJECTED, decide(10, 11, PREFIX + "a b.apk", false, null));
        TestKit.eq("at sign rejected", UpdateDecision.REJECTED, decide(10, 11, PREFIX + "a@b.apk", false, null));
        TestKit.eq("backslash rejected", UpdateDecision.REJECTED, decide(10, 11, PREFIX + "a\\b.apk", false, null));
    }

    private static UpdateDecision decide(long installed, long latest, String url, boolean required, Long min) {
        String json = "{\"version\":\"1\",\"version_code\":" + latest + ",\"url\":\"" + url.replace("\\", "\\\\") + "\",\"size\":1,\"sha256\":\"" + SHA + "\",\"update_required\":" + required
                + (min == null ? "" : ",\"min_supported_code\":" + min) + "}";
        return UpdateDecision.decide(installed, UpdateManifest.parse(json), PREFIX);
    }
    private UpdateDecisionTests() {}
}
