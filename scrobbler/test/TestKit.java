public final class TestKit {
    static int failures;

    static void check(String name, boolean cond) {
        if (cond) {
            System.out.println("PASS: " + name);
        } else {
            failures++;
            System.err.println("FAIL: " + name);
        }
    }

    static void eq(String name, Object want, Object got) {
        check(name + " (want=" + want + ", got=" + got + ")",
                want == null ? got == null : want.equals(got));
    }

    static void section(String name) {
        System.out.println("-- " + name + " --");
    }

    static int finish() {
        System.out.println(failures == 0 ? "ALL TESTS PASS" : failures + " TEST(S) FAILED");
        return failures == 0 ? 0 : 1;
    }

    private TestKit() {}
}
