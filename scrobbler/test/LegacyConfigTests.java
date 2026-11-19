import com.akscrobble.core.ConfigFile;
import com.akscrobble.core.LegacyConfig;

public final class LegacyConfigTests {
    static void run() {
        TestKit.section("LegacyConfig");
        String oldTemplate = "# AKScrobble configuration\n"
                + "#\n"
                + "# Obtain API credentials at https://www.last.fm/api/account/create\n"
                + "# Leave Callback URL and Application homepage empty there.\n"
                + "#\n"
                + "# Fill in api_key and api_secret below, then tap Load configuration\n"
                + "# file on the main screen. These values take precedence over anything\n"
                + "# entered in the app. Check api_key source: config in the status area.\n"
                + "# Leave session_key empty; authentication obtains and stores it.\n"
                + "#\n"
                + "# Lines starting with # or ; are comments.\n"
                + "api_key=\n"
                + "api_secret=\n"
                + "session_key=\n"
                + "user_name=\n";
        LegacyConfig.Result old = LegacyConfig.strip(oldTemplate
                .replace("api_key=\n", "api_key=fake-key\n")
                .replace("api_secret=\n", "api_secret=fake-secret\n")
                .replace("session_key=\n", "session_key=fakeSession1\n"));
        TestKit.check("old template secrets removed", old.changed && !old.meaningful);
        TestKit.check("old template comments removed", !old.text.contains("last.fm/api/account/create")
                && !old.text.contains("Load configuration") && !old.text.contains("take precedence"));
        TestKit.check("old values absent", !old.text.contains("fake-")
                && !old.text.contains("fakeSession1"));

        LegacyConfig.Result mixed = LegacyConfig.strip(
                "api_key=fake-key\r\nsanitize=on\r\n# note\r\n");
        TestKit.eq("mixed content retains original lines", "sanitize=on\r\n# note\r\n", mixed.text);
        TestKit.check("mixed content is changed and meaningful", mixed.changed && mixed.meaningful);

        LegacyConfig.Result spaced = LegacyConfig.strip(" API_KEY = fake-key \n# api_key=fake-key\n");
        TestKit.eq("case and whitespace sensitive key removed", "# api_key=fake-key\n", spaced.text);
        TestKit.check("commented secret retained", spaced.text.contains("# api_key=fake-key"));

        LegacyConfig.Result unchanged = LegacyConfig.strip("sanitize=on\n");
        TestKit.check("no secret input unchanged", !unchanged.changed
                && "sanitize=on\n".equals(unchanged.text));
        LegacyConfig.Result bom = LegacyConfig.strip("\uFEFFapi_key=fake-key\n");
        TestKit.check("BOM retained", bom.text.startsWith("\uFEFF") && bom.changed);
        LegacyConfig.Result nil = LegacyConfig.strip(null);
        TestKit.check("null handled", "".equals(nil.text) && !nil.changed && !nil.meaningful);
    }

    private LegacyConfigTests() {}
}
