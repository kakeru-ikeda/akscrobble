import com.akscrobble.core.LastfmResponse;
import com.akscrobble.core.PendingScrobble;
import com.akscrobble.core.QueuePolicy;
import com.akscrobble.core.SignResult;

public final class QueuePolicyTests {
    static void run() {
        TestKit.section("Queue policy");
        long now = 1000000L;
        TestKit.eq("future 400 seconds is clamped", Long.valueOf(now),
                Long.valueOf(QueuePolicy.clampTimestamp(now + 400L, now)));
        TestKit.eq("future 100 seconds is retained", Long.valueOf(now + 100L),
                Long.valueOf(QueuePolicy.clampTimestamp(now + 100L, now)));
        TestKit.check("14 days plus one second is old",
                QueuePolicy.isTooOld(now - QueuePolicy.MAX_AGE_SEC - 1L, now));
        TestKit.check("14 days minus one second is current",
                !QueuePolicy.isTooOld(now - QueuePolicy.MAX_AGE_SEC + 1L, now));

        TestKit.eq("ok is done", QueuePolicy.Outcome.DONE,
                QueuePolicy.classify(response("ok", 0), 0));
        TestKit.eq("code 9 auth failure blocks", QueuePolicy.Outcome.AUTH_BLOCKED,
                QueuePolicy.classify(response("failed", 9), 0));
        TestKit.eq("code 4 auth failure blocks", QueuePolicy.Outcome.AUTH_BLOCKED,
                QueuePolicy.classify(response("failed", 4), 0));
        TestKit.eq("code 10 is service problem", QueuePolicy.Outcome.SERVICE_PROBLEM,
                QueuePolicy.classify(response("failed", 10), 0));
        TestKit.eq("code 26 is service problem", QueuePolicy.Outcome.SERVICE_PROBLEM,
                QueuePolicy.classify(response("failed", 26), 0));
        TestKit.eq("code 13 retries", QueuePolicy.Outcome.RETRY,
                QueuePolicy.classify(response("failed", 13), 0));
        TestKit.eq("temporary failure retries", QueuePolicy.Outcome.RETRY,
                QueuePolicy.classify(response("failed", 16), 0));
        TestKit.eq("retry limit becomes dead", QueuePolicy.Outcome.DEAD,
                QueuePolicy.classify(response("failed", 16), 9));
        TestKit.eq("permanent failure is dead", QueuePolicy.Outcome.DEAD,
                QueuePolicy.classify(response("failed", 6), 0));
        TestKit.eq("signer temporary failure holds", QueuePolicy.Outcome.HOLD,
                QueuePolicy.classify(signerFailure(503, "app_misconfigured", "{}"), 0));
        TestKit.eq("signer hold ignores retry limit", QueuePolicy.Outcome.HOLD,
                QueuePolicy.classify(signerFailure(503, "app_misconfigured", "{}"), 9));
        TestKit.eq("disabled signer is unavailable", QueuePolicy.Outcome.SIGNER_UNAVAILABLE,
                QueuePolicy.classify(signerFailure(403, "app_disabled", "{}"), 0));
        TestKit.eq("unknown signer is unavailable", QueuePolicy.Outcome.SIGNER_UNAVAILABLE,
                QueuePolicy.classify(signerFailure(404, "unknown_app", "{}"), 0));
        LastfmResponse invalid = signerFailure(400, "invalid_params",
                "{\"error\":{\"code\":\"invalid_params\",\"details\":[{\"param\":\"artist[2]\"}]}}");
        TestKit.eq("indexed invalid params is partial", QueuePolicy.Outcome.PARTIAL_INVALID,
                QueuePolicy.classify(invalid, 0));
        TestKit.eq("invalid index outside batch is omitted", java.util.Collections.emptyList(),
                QueuePolicy.invalidIndices(invalid, 2));
        TestKit.eq("valid invalid index is retained", java.util.Collections.singletonList(2),
                QueuePolicy.invalidIndices(invalid, 3));
        TestKit.eq("invalid params without index retries", QueuePolicy.Outcome.RETRY,
                QueuePolicy.classify(signerFailure(400, "invalid_params",
                        "{\"error\":{\"code\":\"invalid_params\",\"details\":[{\"param\":\"sk\"}]}}"), 0));
        TestKit.eq("rejected payload retries", QueuePolicy.Outcome.RETRY,
                QueuePolicy.classify(signerFailure(413, "payload_too_large", "{}"), 0));
        TestKit.eq("session mismatch blocks auth", QueuePolicy.Outcome.AUTH_BLOCKED,
                QueuePolicy.classify(LastfmResponse.sessionMismatch(), 0));
        TestKit.eq("transport error retries", QueuePolicy.Outcome.RETRY,
                QueuePolicy.classify(LastfmResponse.transportError("offline"), 0));
        TestKit.eq("full queue evicts nothing", Integer.valueOf(0),
                Integer.valueOf(QueuePolicy.evictCount(1000)));
        TestKit.eq("overfull queue evicts excess", Integer.valueOf(5),
                Integer.valueOf(QueuePolicy.evictCount(1005)));
        TestKit.check("ignored code 3 is permanent", QueuePolicy.isIgnoredPermanent(3));
        TestKit.check("ignored code 0 is not permanent", !QueuePolicy.isIgnoredPermanent(0));

        PendingScrobble a = pending("Album A");
        PendingScrobble b = pending("Album B");
        TestKit.eq("album is excluded from dedup key", a.dedupKey(), b.dedupKey());
    }

    private static LastfmResponse response(String status, int code) {
        return LastfmResponse.parse("<lfm status=\"" + status + "\">"
                + (code == 0 ? "" : "<error code=\"" + code + "\">error</error>") + "</lfm>");
    }

    private static LastfmResponse signerFailure(int status, String code, String body) {
        String responseBody = body;
        if ("{}".equals(body)) responseBody = "{\"error\":{\"code\":\"" + code + "\"}}";
        return LastfmResponse.signerFailure(SignResult.parse(status, responseBody));
    }

    private static PendingScrobble pending(String album) {
        return new PendingScrobble(0L, "source", "artist", "track", album, null,
                180000L, 1, 1234L, 0, "pending", null);
    }

    private QueuePolicyTests() {}
}
