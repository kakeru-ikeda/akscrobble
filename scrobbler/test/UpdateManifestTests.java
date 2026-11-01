import com.akscrobble.core.update.MiniJson;
import com.akscrobble.core.update.UpdateManifest;

final class UpdateManifestTests {
    static void run() {
        TestKit.section("UpdateManifest");
        String sha = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789ABCDEF";
        String base = "\"version\":\"1.2.3\",\"version_code\":1002003,\"url\":\"https://nyanstall.sukisuki.club/a.apk\",\"size\":123,\"sha256\":\"" + sha + "\"";
        UpdateManifest m = UpdateManifest.parse("{" + base + ",\"notes\":\"\u3042\",\"min_supported_code\":null,\"update_required\":true,\"codesign\":{\"a\":[1,{\"b\":null}]}}");
        TestKit.eq("version", "1.2.3", m.version);
        TestKit.eq("version code", Long.valueOf(1002003), Long.valueOf(m.versionCode));
        TestKit.eq("sha normalized", sha.toLowerCase(), m.sha256);
        TestKit.eq("escaped Japanese", "あ", m.notes);
        TestKit.check("nullable minimum", m.minSupportedCode == null);
        TestKit.check("required flag", m.updateRequired);
        UpdateManifest withoutOptional = UpdateManifest.parse("{" + base + "}");
        TestKit.check("missing optional values", withoutOptional.minSupportedCode == null && !withoutOptional.updateRequired);
        String[] required = {"version", "version_code", "url", "size", "sha256"};
        for (String key : required) {
            String missing = base.replaceAll("\\\"" + key + "\\\"\\s*:\\s*", "\"removed\":");
            TestKit.check("missing required " + key, fails("{" + missing + "}"));
        }
        TestKit.check("version code type", fails("{" + base.replace("1002003", "\"1002003\"") + "}"));
        TestKit.check("short sha", fails("{" + base.replace(sha, sha.substring(1)) + "}"));
        TestKit.check("non hex sha", fails("{" + base.replace(sha, "g" + sha.substring(1)) + "}"));
        TestKit.check("trailing garbage", fails("{" + base + "} x"));
        TestKit.check("unterminated string", fails("{\"version\":\"broken"));
        TestKit.check("nested json parsed", MiniJson.parse("{\"codesign\":{\"a\":[1,{\"b\":null}]}}") instanceof java.util.Map);
    }

    private static boolean fails(String json) {
        try { UpdateManifest.parse(json); return false; }
        catch (IllegalArgumentException expected) { return true; }
    }
    private UpdateManifestTests() {}
}
