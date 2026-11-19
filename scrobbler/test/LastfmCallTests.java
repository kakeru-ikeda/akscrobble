import com.akscrobble.core.LastfmCall;
import com.akscrobble.core.LastfmRequest;
import com.akscrobble.core.LastfmResponse;
import com.akscrobble.core.LastfmTransport;
import com.akscrobble.core.PendingScrobble;
import com.akscrobble.core.SignResult;
import com.akscrobble.core.Signer;
import com.akscrobble.core.TrackInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class LastfmCallTests {
    static void run() {
        TestKit.section("Signed Last.fm calls");
        SignResult success = success();
        FakeSigner signer = new FakeSigner(success);
        FakeTransport transport = new FakeTransport(ok(), ok());
        LastfmCall call = new LastfmCall(signer, transport, "fakeSession1", "fake-key");
        LastfmResponse sent = call.scrobble(Arrays.asList(pending("Artist & Co", "曲名")));
        TestKit.eq("scrobble signer method", "track.scrobble", signer.methods.get(0));
        TestKit.check("signer params omit api key", !signer.params.get(0).containsKey("api_key"));
        TestKit.check("signer params omit method", !signer.params.get(0).containsKey("method"));
        TestKit.eq("signer receives original artist", "Artist & Co", signer.params.get(0).get("artist[0]"));
        TestKit.eq("signed body has signer api key", "fake-key", value(transport.bodies.get(0), "api_key"));
        TestKit.eq("signed body has signer signature", "fake-sig", value(transport.bodies.get(0), "api_sig"));
        TestKit.eq("signed body preserves encoded artist", "Artist+%26+Co",
                value(transport.bodies.get(0), "artist%5B0%5D"));
        TestKit.eq("signed body preserves encoded track", "%E6%9B%B2%E5%90%8D",
                value(transport.bodies.get(0), "track%5B0%5D"));
        TestKit.eq("response carries signing api key", "fake-key", sent.apiKey);

        FakeSigner sessionSigner = new FakeSigner(success, success);
        FakeTransport sessionTransport = new FakeTransport(ok(), ok(), ok());
        LastfmCall sessionCall = new LastfmCall(sessionSigner, sessionTransport, "", "");
        sessionCall.getSession("tok1");
        sessionCall.getSession("tok1");
        TestKit.eq("session signature cached per token", Integer.valueOf(1),
                Integer.valueOf(sessionSigner.methods.size()));
        sessionCall.getSession("tok2");
        TestKit.eq("new token gets new signature", Integer.valueOf(2),
                Integer.valueOf(sessionSigner.methods.size()));

        FakeSigner retryCacheSigner = new FakeSigner(retryable(), success());
        LastfmCall retryCacheCall = new LastfmCall(retryCacheSigner,
                new FakeTransport(ok()), "", "");
        retryCacheCall.getSession("tok1");
        retryCacheCall.getSession("tok1");
        TestKit.eq("failed signature is not cached", Integer.valueOf(2),
                Integer.valueOf(retryCacheSigner.methods.size()));

        FakeSigner resignSigner = new FakeSigner(success(), success(), success());
        FakeTransport resignTransport = new FakeTransport(error(13), error(13), ok());
        LastfmCall resignCall = new LastfmCall(resignSigner, resignTransport, "fakeSession1", "fake-key");
        LastfmResponse thirteen = resignCall.scrobble(Arrays.asList(pending("Artist", "Track")));
        TestKit.eq("error 13 retries with a new signature", Integer.valueOf(2),
                Integer.valueOf(resignSigner.methods.size()));
        TestKit.eq("error 13 retries transport once", Integer.valueOf(2),
                Integer.valueOf(resignTransport.bodies.size()));
        TestKit.eq("second error 13 is returned", Integer.valueOf(13), Integer.valueOf(thirteen.errorCode));

        FakeSigner mismatchSigner = new FakeSigner(success());
        FakeTransport mismatchTransport = new FakeTransport(ok());
        LastfmResponse mismatch = new LastfmCall(mismatchSigner, mismatchTransport,
                "fakeSession1", "fake-key-old").scrobble(Arrays.asList(pending("Artist", "Track")));
        TestKit.eq("changed api key produces auth failure", Integer.valueOf(9), Integer.valueOf(mismatch.errorCode));
        TestKit.check("changed api key is auth failure", mismatch.isAuthFailure());
        TestKit.eq("changed api key skips transport", Integer.valueOf(0),
                Integer.valueOf(mismatchTransport.bodies.size()));
        LastfmResponse missingApiKey = new LastfmCall(new FakeSigner(success()), new FakeTransport(ok()),
                "fakeSession1", "").scrobble(Arrays.asList(pending("Artist", "Track")));
        TestKit.eq("empty saved api key is mismatch", Integer.valueOf(9), Integer.valueOf(missingApiKey.errorCode));

        FakeSigner unavailableSigner = new FakeSigner(SignResult.parse(404,
                "{\"error\":{\"code\":\"unknown_app\"}}"));
        FakeTransport unavailableTransport = new FakeTransport(ok());
        LastfmResponse unavailable = new LastfmCall(unavailableSigner, unavailableTransport,
                "fakeSession1", "fake-key").scrobble(Arrays.asList(pending("Artist", "Track")));
        TestKit.check("signer failure is identified", unavailable.isSignerFailure());
        TestKit.eq("signer kind is retained", SignResult.Kind.UNAVAILABLE, unavailable.signerKind);
        TestKit.eq("signer failure skips transport", Integer.valueOf(0),
                Integer.valueOf(unavailableTransport.bodies.size()));

        FakeSigner invalidSigner = new FakeSigner(success());
        LastfmResponse invalid = new LastfmCall(invalidSigner, new FakeTransport(ok()),
                "fakeSession1", "fake-key").scrobble(Arrays.asList(
                        pending("Artist 0", "Track 0"), pending("", "Track 1")));
        TestKit.eq("invalid local batch param skips signer", Integer.valueOf(0),
                Integer.valueOf(invalidSigner.methods.size()));
        TestKit.eq("invalid local param is classified", SignResult.Kind.INVALID_PARAMS, invalid.signerKind);
        TestKit.eq("invalid local param points to batch item", Arrays.asList(Integer.valueOf(1)),
                invalid.signerInvalidIndices);

        FakeSigner missingSessionSigner = new FakeSigner(success());
        LastfmResponse missingSession = new LastfmCall(missingSessionSigner, new FakeTransport(ok()), "", "")
                .scrobble(Arrays.asList(pending("Artist", "Track")));
        TestKit.eq("missing session has auth error", Integer.valueOf(9), Integer.valueOf(missingSession.errorCode));
        TestKit.eq("missing session skips signer", Integer.valueOf(0),
                Integer.valueOf(missingSessionSigner.methods.size()));

        TreeMap<String, String> token = LastfmRequest.getToken();
        TreeMap<String, String> expectedToken = new TreeMap<String, String>();
        expectedToken.put("method", "auth.getToken");
        TestKit.eq("unsigned get token only has method", expectedToken, token);
    }

    private static String value(String body, String key) {
        String prefix = key + "=";
        for (String part : body.split("&")) if (part.startsWith(prefix)) return part.substring(prefix.length());
        return null;
    }

    private static SignResult success() {
        return SignResult.parse(200, "{\"api_key\":\"fake-key\",\"api_sig\":\"fake-sig\"}");
    }

    private static SignResult retryable() {
        return SignResult.parse(503, "{}");
    }

    private static LastfmResponse ok() {
        return LastfmResponse.parse("<lfm status=\"ok\"/>");
    }

    private static LastfmResponse error(int code) {
        return LastfmResponse.parse("<lfm status=\"failed\"><error code=\"" + code + "\">failed</error></lfm>");
    }

    private static PendingScrobble pending(String artist, String track) {
        return new PendingScrobble(0L, "source", artist, track, null, null,
                0L, 0, 100L, 0, "pending", null);
    }

    private static final class FakeSigner implements Signer {
        final List<String> methods = new ArrayList<String>();
        final List<Map<String, String>> params = new ArrayList<Map<String, String>>();
        final List<SignResult> results;
        int next;

        FakeSigner(SignResult... results) { this.results = Arrays.asList(results); }

        public SignResult sign(String method, Map<String, String> values) {
            methods.add(method);
            params.add(new TreeMap<String, String>(values));
            SignResult result = results.get(Math.min(next, results.size() - 1));
            next++;
            return result;
        }
    }

    private static final class FakeTransport implements LastfmTransport {
        final List<String> bodies = new ArrayList<String>();
        final List<LastfmResponse> responses;
        int next;

        FakeTransport(LastfmResponse... responses) { this.responses = Arrays.asList(responses); }

        public LastfmResponse post(String body) {
            bodies.add(body);
            LastfmResponse response = responses.get(Math.min(next, responses.size() - 1));
            next++;
            return response;
        }
    }

    private LastfmCallTests() {}
}
