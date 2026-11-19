import com.akscrobble.core.ConfigFile;
import com.akscrobble.core.Credentials;
import java.util.HashMap;
import java.util.Map;

public final class ConfigTests {
    static void run() {
        TestKit.section("Config");
        Map<String, String> parsed = ConfigFile.parse("\uFEFF# comment\n; another\n\n"
                + " API_KEY = abc \nSESSION_KEY=a=b=c\nignored\nempty=\n");
        TestKit.eq("BOM and case normalized", "abc", parsed.get("api_key"));
        TestKit.eq("value preserves equals", "a=b=c", parsed.get("session_key"));
        TestKit.eq("empty value retained", "", parsed.get("empty"));
        TestKit.check("comments and invalid lines ignored", parsed.size() == 3);
        TestKit.check("template omits API credentials",
                !ConfigFile.template().contains("\napi_key=")
                        && !ConfigFile.template().contains("\napi_secret="));
        TestKit.check("template contains session fields",
                ConfigFile.template().contains("session_key=")
                        && ConfigFile.template().contains("session_api_key=")
                        && ConfigFile.template().contains("user_name="));
        TestKit.check("template describes in-app authorization",
                ConfigFile.template().contains("Last.fm authorization is done in the app"));
        TestKit.eq("template parses session key as empty", "",
                ConfigFile.parse(ConfigFile.template()).get("session_key"));

        Map<String, String> file = map("api_key", "file-key", "api_secret", "",
                "session_key", "file-session", "session_api_key", "file-session-key");
        Map<String, String> prefs = map("api_key", "prefs-key", "api_secret", "prefs-secret",
                "session_key", "prefs-session", "session_api_key", "prefs-session-key");
        Credentials resolved = Credentials.resolve(file, prefs, "builtin-key", "builtin-secret");
        TestKit.eq("file wins per field", "file-key", resolved.apiKey);
        TestKit.eq("empty file falls through to prefs", "prefs-secret", resolved.apiSecret);
        TestKit.eq("session resolves independently", "file-session", resolved.sessionKey);
        TestKit.eq("session API key resolves independently", "file-session-key", resolved.sessionApiKey);
        TestKit.eq("config source", "config", resolved.source("api_key"));
        TestKit.eq("prefs source", "prefs", resolved.source("apiSecret"));
        TestKit.check("complete credentials", resolved.isComplete());

        Credentials sessionOnly = Credentials.resolve(new HashMap<String, String>(),
                map("session_key", "session", "session_api_key", "session-key"), "", "");
        TestKit.check("session pair is complete without API credentials", sessionOnly.isComplete());
        Credentials missingSessionApiKey = Credentials.resolve(new HashMap<String, String>(),
                map("session_key", "session"), "builtin-key", "builtin-secret");
        TestKit.check("session API key is required", !missingSessionApiKey.isComplete());

        String fingerprint = sessionOnly.fingerprint();
        TestKit.check("fingerprint is lowercase MD5", fingerprint.matches("[0-9a-f]{32}"));
        TestKit.check("fingerprint changes with session API key", !fingerprint.equals(
                Credentials.resolve(new HashMap<String, String>(),
                        map("session_key", "session", "session_api_key", "other-key"), "", "")
                        .fingerprint()));
        TestKit.check("fingerprint changes with session key", !fingerprint.equals(
                Credentials.resolve(new HashMap<String, String>(),
                        map("session_key", "other-session", "session_api_key", "session-key"), "", "")
                        .fingerprint()));

        file.put("api_key", "   ");
        prefs.put("api_key", "");
        Credentials whitespace = Credentials.resolve(file, prefs, "fallback", "secret");
        TestKit.eq("blank values are unset", "fallback", whitespace.apiKey);
    }

    private static Map<String, String> map(String... values) {
        Map<String, String> result = new HashMap<String, String>();
        for (int i = 0; i < values.length; i += 2) result.put(values[i], values[i + 1]);
        return result;
    }

    private ConfigTests() {}
}
