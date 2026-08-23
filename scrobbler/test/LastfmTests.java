import com.akscrobble.core.LastfmRequest;
import com.akscrobble.core.LastfmResponse;
import com.akscrobble.core.LastfmSig;
import com.akscrobble.core.PendingScrobble;
import com.akscrobble.core.TrackInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public final class LastfmTests {
    static void run() {
        TestKit.section("Last.fm protocol");
        TestKit.eq("MD5 known vector", "098f6bcd4621d373cade4e832627b4f6",
                LastfmSig.hexMd5("test"));

        TreeMap<String, String> sigParams = new TreeMap<String, String>();
        sigParams.put("b", "2");
        sigParams.put("a", "1");
        sigParams.put("format", "json");
        sigParams.put("callback", "cb");
        sigParams.put("api_sig", "old");
        TestKit.eq("signature sorts and excludes control keys",
                LastfmSig.hexMd5("a1b2secret"), LastfmSig.sign(sigParams, "secret"));
        TestKit.eq("UTF-8 signature is stable", LastfmSig.sign(single("track", "日本語"), "秘密"),
                LastfmSig.sign(single("track", "日本語"), "秘密"));

        List<PendingScrobble> items = new ArrayList<PendingScrobble>();
        items.add(pending("Artist 0", "曲名", null, 216000L, 0, 100L));
        items.add(pending("Artist 1", "Track 1", "Album", 0L, 2, 200L));
        TreeMap<String, String> batch = LastfmRequest.scrobbleBatch(items, "key", "session");
        TestKit.eq("batch starts at index zero", "Artist 0", batch.get("artist[0]"));
        TestKit.eq("batch indices are consecutive", "Artist 1", batch.get("artist[1]"));
        TestKit.eq("duration uses seconds", "216", batch.get("duration[0]"));
        TestKit.check("null album is omitted", !batch.containsKey("album[0]"));
        TestKit.check("zero duration is omitted", !batch.containsKey("duration[1]"));
        String body = LastfmRequest.signedBody(batch, "secret");
        TestKit.check("body contains signature", body.contains("api_sig="));
        TestKit.check("body percent-encodes Japanese", body.contains("%E6%9B%B2%E5%90%8D"));

        LastfmResponse ok = LastfmResponse.parse(
                "<lfm status=\"ok\"><scrobbles accepted=\"1\" ignored=\"0\"/></lfm>");
        TestKit.check("successful response", ok.isOk());
        TestKit.eq("accepted count", Integer.valueOf(1), Integer.valueOf(ok.accepted));
        TestKit.eq("ignored count", Integer.valueOf(0), Integer.valueOf(ok.ignored));

        LastfmResponse auth = LastfmResponse.parse(
                "<lfm status=\"failed\"><error code=\"9\">Bad Session</error></lfm>");
        TestKit.check("auth failure classification", auth.isAuthFailure());
        TestKit.check("retryable classification", LastfmResponse.parse(
                "<lfm status=\"failed\"><error code=\"16\">Temporary</error></lfm>").isRetryable());
        TestKit.check("permanent classification", LastfmResponse.parse(
                "<lfm status=\"failed\"><error code=\"6\">Bad params</error></lfm>").isPermanent());

        LastfmResponse ignored = LastfmResponse.parse("<lfm status=\"ok\"><scrobbles accepted=\"0\" ignored=\"1\">"
                + "<scrobble><ignoredMessage code=\"3\">Timestamp too old</ignoredMessage></scrobble>"
                + "</scrobbles></lfm>");
        TestKit.eq("ignored message code", Integer.valueOf(3), ignored.ignoredCodes.get(0));

        LastfmResponse session = LastfmResponse.parse(
                "<lfm status=\"ok\"><session><name>Alice</name><key>abc</key></session></lfm>");
        TestKit.eq("session key", "abc", session.sessionKey);
        TestKit.eq("session name", "Alice", session.sessionName);
        // 応答の分類。ここを誤ると scrobble を永久リトライするか永久に捨てる。
        TestKit.eq("code 9 is auth failure", Boolean.TRUE, Boolean.valueOf(
                LastfmResponse.parse("<lfm status=\"failed\"><error code=\"9\">bad sk</error></lfm>")
                        .isAuthFailure()));
        TestKit.eq("code 29 is retryable", Boolean.TRUE, Boolean.valueOf(
                LastfmResponse.parse("<lfm status=\"failed\"><error code=\"29\">rate</error></lfm>")
                        .isRetryable()));
        TestKit.eq("code 6 is permanent", Boolean.TRUE, Boolean.valueOf(
                LastfmResponse.parse("<lfm status=\"failed\"><error code=\"6\">params</error></lfm>")
                        .isPermanent()));
        TestKit.eq("malformed xml is retryable", Boolean.TRUE, Boolean.valueOf(
                LastfmResponse.parse("<lfm status=\"ok\"><unclosed>").isRetryable()));
        TestKit.eq("empty body is retryable", Boolean.TRUE, Boolean.valueOf(
                LastfmResponse.parse("").isRetryable()));
        // 5xx の HTML エラーページは XML として整形式なので解析に成功してしまう。
        // 恒久エラー扱いにすると一時障害で scrobble を捨てるため、リトライ可であること。
        TestKit.eq("html error page is retryable", Boolean.TRUE, Boolean.valueOf(
                LastfmResponse.parse("<html><body>502 Bad Gateway</body></html>").isRetryable()));
        TestKit.eq("html error page is not permanent", Boolean.FALSE, Boolean.valueOf(
                LastfmResponse.parse("<html><body>502 Bad Gateway</body></html>").isPermanent()));
        TestKit.eq("auth token", "tok", LastfmResponse.parse(
                "<lfm status=\"ok\"><token>tok</token></lfm>").token);
        TestKit.check("broken XML becomes failure", !LastfmResponse.parse("<lfm").isOk());
        TestKit.check("empty XML becomes failure", !LastfmResponse.parse("").isOk());
        TestKit.check("transport failures retry", LastfmResponse.transportError("offline").isRetryable());
    }

    private static TreeMap<String, String> single(String key, String value) {
        TreeMap<String, String> map = new TreeMap<String, String>();
        map.put(key, value);
        return map;
    }

    private static PendingScrobble pending(String artist, String track, String album,
            long durationMs, int trackNumber, long timestamp) {
        return new PendingScrobble(0L, "source", artist, track, album, null,
                durationMs, trackNumber, timestamp, 0, "pending", null);
    }

    private LastfmTests() {}
}
