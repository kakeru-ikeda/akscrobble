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
        TestKit.check("template contains credential fields",
                ConfigFile.template().contains("api_key=")
                        && ConfigFile.template().contains("api_secret=")
                        && ConfigFile.template().contains("session_key="));
        // 端末で直接開いた人がここだけで完結できるよう、雛形に案内を持たせている。
        // README の「設定ファイルが主経路」という記述と対になる。
        TestKit.check("template points at the API account page",
                ConfigFile.template().contains("last.fm/api/account/create"));
        TestKit.check("template says the callback fields stay empty",
                ConfigFile.template().contains("Callback URL"));
        TestKit.check("template says no button press is needed",
                ConfigFile.template().contains("no button needs to be pressed"));
        TestKit.check("template says it wins over in-app values",
                ConfigFile.template().contains("take precedence"));
        // 雛形は自分自身の書式で読み戻せること。コメント行が parse を壊さない保証。
        TestKit.eq("template parses back to empty values", "",
                ConfigFile.parse(ConfigFile.template()).get("api_key"));

        Map<String, String> file = map("api_key", "file-key", "api_secret", "",
                "session_key", "file-session");
        Map<String, String> prefs = map("api_key", "prefs-key", "api_secret", "prefs-secret",
                "session_key", "prefs-session");
        Credentials resolved = Credentials.resolve(file, prefs, "builtin-key", "builtin-secret");
        TestKit.eq("file wins per field", "file-key", resolved.apiKey);
        TestKit.eq("empty file falls through to prefs", "prefs-secret", resolved.apiSecret);
        TestKit.eq("session resolves independently", "file-session", resolved.sessionKey);
        TestKit.eq("config source", "config", resolved.source("api_key"));
        TestKit.eq("prefs source", "prefs", resolved.source("apiSecret"));
        TestKit.check("complete credentials", resolved.isComplete());

        Credentials builtin = Credentials.resolve(new HashMap<String, String>(),
                new HashMap<String, String>(), "built-key", "built-secret");
        TestKit.eq("builtin source", "builtin", builtin.source("apiKey"));
        TestKit.eq("missing source", "none", builtin.source("sessionKey"));
        TestKit.check("missing session is incomplete", !builtin.isComplete());

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
