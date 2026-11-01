import com.akscrobble.core.Credentials;
import com.akscrobble.core.LaunchRoute;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class LaunchRouteTests {
    static void run() {
        TestKit.section("LaunchRoute");
        TestKit.eq("missing key goes to setup", LaunchRoute.SETUP,
                route("", "secret", "session", "", ""));
        TestKit.eq("missing secret goes to setup", LaunchRoute.SETUP,
                route("key", "", "session", "", ""));
        TestKit.eq("blank values go to setup", LaunchRoute.SETUP,
                route("  ", " secret ", " session ", "", ""));
        TestKit.eq("keys without session go to auth", LaunchRoute.AUTH,
                route("key", "secret", "", "", ""));
        TestKit.eq("complete credentials go home", LaunchRoute.HOME,
                route("key", "secret", "session", "", ""));
        TestKit.eq("builtin keys do not require setup", LaunchRoute.AUTH,
                CredentialsRoute.builtin());
        TestKit.eq("null credentials go to setup", LaunchRoute.SETUP,
                LaunchRoute.decide(null));
    }

    private static LaunchRoute route(String key, String secret, String session,
            String builtinKey, String builtinSecret) {
        Map<String, String> prefs = new HashMap<String, String>();
        prefs.put("api_key", key);
        prefs.put("api_secret", secret);
        prefs.put("session_key", session);
        return LaunchRoute.decide(Credentials.resolve(Collections.<String, String>emptyMap(),
                prefs, builtinKey, builtinSecret));
    }

    private static final class CredentialsRoute {
        static LaunchRoute builtin() {
            return LaunchRoute.decide(Credentials.resolve(null, null, " builtin-key ",
                    " builtin-secret "));
        }
    }

    private LaunchRouteTests() {}
}
