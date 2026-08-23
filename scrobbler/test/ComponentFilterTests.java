import com.akscrobble.core.ComponentFilter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ComponentFilterTests {
    static void run() {
        TestKit.section("ComponentFilter");

        ComponentFilter.Split split = ComponentFilter.classify(Arrays.asList(
                "First", "UserDebugActivity", "DebugSettingActivity", "Last"));
        TestKit.eq("DebugSetting wins over UserDebug",
                Arrays.asList("DebugSettingActivity"), split.primary);
        TestKit.eq("non-primary entries retain input order",
                Arrays.asList("First", "UserDebugActivity", "Last"), split.secondary);

        split = ComponentFilter.classify(Arrays.asList("First", "UserDebugActivity", "Last"));
        TestKit.eq("UserDebug is the fallback primary",
                Arrays.asList("UserDebugActivity"), split.primary);
        TestKit.eq("UserDebug fallback preserves secondary order",
                Arrays.asList("First", "Last"), split.secondary);

        split = ComponentFilter.classify(Arrays.asList("First", "Second"));
        TestKit.eq("no match leaves primary empty", Collections.emptyList(), split.primary);
        TestKit.eq("no match leaves all entries secondary",
                Arrays.asList("First", "Second"), split.secondary);

        assertEmpty("empty input", ComponentFilter.classify(Collections.<String>emptyList()));
        assertEmpty("null input", ComponentFilter.classify(null));

        split = ComponentFilter.classify(Arrays.asList(
                null, "", "  ", "UserDebugOne", "middle", "UserDebugTwo"));
        TestKit.eq("null and blank entries are discarded",
                Arrays.asList("UserDebugOne", "UserDebugTwo"), split.primary);
        TestKit.eq("filtered input order is retained",
                Arrays.asList("middle"), split.secondary);

        split = ComponentFilter.classify(Arrays.asList("debugsetting", "Ordinary"));
        TestKit.eq("matching is case-sensitive", Collections.emptyList(), split.primary);
        TestKit.eq("lowercase name remains secondary",
                Arrays.asList("debugsetting", "Ordinary"), split.secondary);

        final List<String> primary = ComponentFilter.classify(
                Arrays.asList("DebugSettingActivity")).primary;
        TestKit.check("primary list is unmodifiable", isUnmodifiable(primary));
        final List<String> secondary = ComponentFilter.classify(
                Arrays.asList("Ordinary")).secondary;
        TestKit.check("secondary list is unmodifiable", isUnmodifiable(secondary));
    }

    private static void assertEmpty(String label, ComponentFilter.Split split) {
        TestKit.check(label, split.primary.isEmpty() && split.secondary.isEmpty());
    }

    private static boolean isUnmodifiable(List<String> values) {
        try {
            values.add("changed");
            return false;
        } catch (UnsupportedOperationException expected) {
            return true;
        }
    }

    private ComponentFilterTests() {}
}
