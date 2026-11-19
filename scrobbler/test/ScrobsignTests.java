import com.akscrobble.core.Scrobsign;
import com.akscrobble.core.SignResult;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public final class ScrobsignTests {
    public static void run() {
        TestKit.section("Scrobsign");
        TestKit.eq("sign URL", "https://scrobsign.sukisuki.club/v1/akscrobble/sign", Scrobsign.SIGN_URL);
        TestKit.eq("connect timeout", Integer.valueOf(10000), Integer.valueOf(Scrobsign.CONNECT_TIMEOUT_MS));
        TestKit.eq("read timeout", Integer.valueOf(15000), Integer.valueOf(Scrobsign.READ_TIMEOUT_MS));
        TestKit.eq("response limit", Integer.valueOf(64 * 1024), Integer.valueOf(Scrobsign.MAX_RESPONSE_BYTES));

        Map<String, String> source = new HashMap<String, String>();
        source.put("method", "track.scrobble");
        source.put("api_key", "fake-key");
        source.put("api_sig", "fake-sig");
        source.put("format", "json");
        source.put("callback", "cb");
        source.put("artist", " Spaced ");
        TreeMap<String, String> signable = Scrobsign.signableParams(source);
        TestKit.eq("signable params remove reserved keys", Collections.singleton("artist"), signable.keySet());
        TestKit.eq("signable params preserve values", " Spaced ", signable.get("artist"));
        TestKit.check("signable params return a copy", signable != source);

        TestKit.eq("empty request JSON", "{\"method\":\"auth.getToken\",\"params\":{}}",
                Scrobsign.requestJson("auth.getToken", Collections.<String, String>emptyMap()));
        Map<String, String> ordered = new HashMap<String, String>();
        ordered.put("track[0]", "曲名");
        ordered.put("timestamp[0]", "1");
        ordered.put("sk", "fakeSession1");
        ordered.put("artist[0]", "A\"B\\C\u0001");
        TestKit.eq("sorted and escaped request JSON",
                "{\"method\":\"track.scrobble\",\"params\":{\"artist[0]\":\"A\\\"B\\\\C\\u0001\",\"sk\":\"fakeSession1\",\"timestamp[0]\":\"1\",\"track[0]\":\"曲名\"}}",
                Scrobsign.requestJson("track.scrobble", ordered));
        expectIllegal("request rejects null method", new Runnable() { public void run() {
            Scrobsign.requestJson(null, Collections.<String, String>emptyMap());
        }});
        expectIllegal("request rejects empty method", new Runnable() { public void run() {
            Scrobsign.requestJson("", Collections.<String, String>emptyMap());
        }});
        expectIllegal("request rejects null params", new Runnable() { public void run() {
            Scrobsign.requestJson("method", null);
        }});
        expectIllegal("request rejects reserved key", new Runnable() { public void run() {
            Scrobsign.requestJson("method", Collections.singletonMap("api_key", "fake-key"));
        }});
        Map<String, String> nullValue = new HashMap<String, String>();
        nullValue.put("artist", null);
        expectIllegal("request rejects null value", new Runnable() { public void run() {
            Scrobsign.requestJson("method", nullValue);
        }});

        SignResult success = SignResult.parse(200, "{\"api_key\":\" fake-key \",\"api_sig\":\"fake-sig\"}");
        TestKit.eq("valid response is OK", SignResult.Kind.OK, success.kind);
        TestKit.eq("response key is preserved", " fake-key ", success.apiKey);
        TestKit.eq("response signature is preserved", "fake-sig", success.apiSig);
        TestKit.eq("empty response fields are malformed", SignResult.Kind.RETRYABLE,
                SignResult.parse(200, "{\"api_key\":\"\",\"api_sig\":\"x\"}").kind);
        TestKit.eq("missing response fields are malformed", SignResult.Kind.RETRYABLE, SignResult.parse(200, "{}").kind);
        TestKit.eq("HTML response is malformed", SignResult.Kind.RETRYABLE, SignResult.parse(200, "<html>").kind);
        TestKit.eq("non-string response field is malformed", "malformed_response",
                SignResult.parse(200, "{\"api_key\":1,\"api_sig\":\"x\"}").errorCode);

        expectKind("app_disabled", SignResult.Kind.UNAVAILABLE);
        expectKind("method_forbidden", SignResult.Kind.UNAVAILABLE);
        expectKind("unknown_app", SignResult.Kind.UNAVAILABLE);
        expectKind("invalid_params", SignResult.Kind.INVALID_PARAMS);
        expectKind("rate_limited", SignResult.Kind.RETRYABLE);
        expectKind("app_misconfigured", SignResult.Kind.RETRYABLE);
        expectKind("internal", SignResult.Kind.RETRYABLE);
        expectKind("invalid_request", SignResult.Kind.REJECTED);
        expectKind("method_not_allowed", SignResult.Kind.REJECTED);
        expectKind("payload_too_large", SignResult.Kind.REJECTED);
        expectKind("unsupported_media_type", SignResult.Kind.REJECTED);
        SignResult invalid = SignResult.parse(400,
                "{\"error\":{\"code\":\"invalid_params\",\"message\":\"bad\",\"details\":["
                + "{\"param\":\"artist[3]\",\"reason\":\"bad\"},"
                + "{\"param\":\"track[3]\"},{\"param\":\"timestamp[12]\"},{\"param\":\"sk\"}]}}");
        TestKit.eq("invalid param names retained", Arrays.asList("artist[3]", "track[3]", "timestamp[12]", "sk"), invalid.invalidParams);
        TestKit.eq("invalid indices are unique and sorted", Arrays.asList(Integer.valueOf(3), Integer.valueOf(12)), invalid.invalidIndices());
        TestKit.eq("unknown code falls back to HTTP", SignResult.Kind.RETRYABLE,
                SignResult.parse(429, "{\"error\":{\"code\":\"future_code\"}}").kind);
        TestKit.eq("malformed 403 falls back to HTTP", SignResult.Kind.RETRYABLE, SignResult.parse(403, "forbidden").kind);
        TestKit.eq("502 HTML is retryable", SignResult.Kind.RETRYABLE, SignResult.parse(502, "<html>Bad Gateway</html>").kind);
        TestKit.eq("empty 404 is retryable", SignResult.Kind.RETRYABLE, SignResult.parse(404, "").kind);
        TestKit.eq("418 is rejected", SignResult.Kind.REJECTED, SignResult.parse(418, "").kind);
        TestKit.eq("transport error is retryable", SignResult.Kind.RETRYABLE, SignResult.transportError("x").kind);
        TestKit.eq("transport error code", "transport", SignResult.transportError("x").errorCode);
        TestKit.eq("local invalid params", SignResult.Kind.INVALID_PARAMS,
                SignResult.invalidLocally(Collections.singletonList("artist[0]")).kind);
        TestKit.eq("invalid params list is immutable", Boolean.FALSE, isMutable(invalid.invalidParams));
        TestKit.check("toString omits credentials", !success.toString().contains("fake-key") && !success.toString().contains("fake-sig"));
    }

    private static void expectKind(String code, SignResult.Kind kind) {
        String body = "{\"error\":{\"code\":\"" + code + "\",\"message\":\"detail\"}}";
        TestKit.eq(code + " classification", kind, SignResult.parse(400, body).kind);
    }

    private static void expectIllegal(String name, Runnable action) {
        boolean thrown = false;
        try { action.run(); } catch (IllegalArgumentException expected) { thrown = true; }
        TestKit.check(name, thrown);
    }

    private static boolean isMutable(java.util.List<String> values) {
        try { values.add("mutation"); return true; } catch (UnsupportedOperationException expected) { return false; }
    }

    private ScrobsignTests() {}
}
