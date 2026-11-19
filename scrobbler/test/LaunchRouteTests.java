import com.akscrobble.core.Credentials;
import com.akscrobble.core.LaunchRoute;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class LaunchRouteTests {
    static void run() {
        TestKit.section("LaunchRoute");
        TestKit.eq("missing session goes to setup", LaunchRoute.SETUP,
                route("", "", false));
        TestKit.eq("dismissed setup goes to auth", LaunchRoute.AUTH,
                route("", "", true));
        TestKit.eq("incomplete session goes to setup", LaunchRoute.SETUP,
                route("session", "", false));
        TestKit.eq("complete session goes home", LaunchRoute.HOME,
                route("session", "session-key", false));
        TestKit.eq("dismissed does not affect complete session", LaunchRoute.HOME,
                route("session", "session-key", true));
        TestKit.eq("null credentials go to setup", LaunchRoute.SETUP,
                LaunchRoute.decide(null, false));
    }

    private static LaunchRoute route(String session, String sessionApiKey,
            boolean setupDismissed) {
        Map<String, String> prefs = new HashMap<String, String>();
        prefs.put("session_key", session);
        prefs.put("session_api_key", sessionApiKey);
        return LaunchRoute.decide(Credentials.resolve(
                Collections.<String, String>emptyMap(), prefs), setupDismissed);
    }

    private LaunchRouteTests() {}
}
