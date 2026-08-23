import com.akscrobble.core.BackoffPolicy;

public final class BackoffTests {
    static void run() {
        TestKit.section("BackoffPolicy");
        TestKit.eq("zero failures", Long.valueOf(0L), Long.valueOf(BackoffPolicy.nextDelayMs(0)));
        TestKit.eq("first failure", Long.valueOf(30000L), Long.valueOf(BackoffPolicy.nextDelayMs(1)));
        TestKit.eq("second failure", Long.valueOf(120000L), Long.valueOf(BackoffPolicy.nextDelayMs(2)));
        TestKit.eq("fifth failure", Long.valueOf(3600000L), Long.valueOf(BackoffPolicy.nextDelayMs(5)));
        TestKit.eq("delay capped", Long.valueOf(3600000L), Long.valueOf(BackoffPolicy.nextDelayMs(99)));
        long previous = BackoffPolicy.nextDelayMs(0);
        for (int failures = 1; failures < 100; failures++) {
            long current = BackoffPolicy.nextDelayMs(failures);
            TestKit.check("nondecreasing at " + failures, current >= previous);
            previous = current;
        }
    }

    private BackoffTests() {}
}
