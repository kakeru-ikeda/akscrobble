import com.akscrobble.core.LastfmResponse;
import com.akscrobble.core.PendingScrobble;
import com.akscrobble.core.QueuePolicy;

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
        TestKit.eq("auth failure blocks", QueuePolicy.Outcome.AUTH_BLOCKED,
                QueuePolicy.classify(response("failed", 9), 0));
        TestKit.eq("temporary failure retries", QueuePolicy.Outcome.RETRY,
                QueuePolicy.classify(response("failed", 16), 0));
        TestKit.eq("retry limit becomes dead", QueuePolicy.Outcome.DEAD,
                QueuePolicy.classify(response("failed", 16), 9));
        TestKit.eq("permanent failure is dead", QueuePolicy.Outcome.DEAD,
                QueuePolicy.classify(response("failed", 6), 0));
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

    private static PendingScrobble pending(String album) {
        return new PendingScrobble(0L, "source", "artist", "track", album, null,
                180000L, 1, 1234L, 0, "pending", null);
    }

    private QueuePolicyTests() {}
}
