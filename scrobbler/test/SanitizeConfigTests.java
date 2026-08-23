import com.akscrobble.core.SanitizeConfig;
import com.akscrobble.core.SanitizeRule;
import com.akscrobble.core.Sanitizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SanitizeConfigTests {
    static void run() {
        TestKit.section("SanitizeConfig");
        roundTrip(true);
        roundTrip(false);

        char sep = SanitizeConfig.SEP;
        String malformed = "enabled" + sep + "0\n"
                + "disabled" + sep + "trim-ends\n"
                + "rule" + sep + "bad-regex" + sep + "ALL" + sep
                + "[unclosed" + sep + "\n"
                + "rule" + sep + "good" + sep + "TRACK" + sep + "x" + sep + "y";
        SanitizeConfig parsed = SanitizeConfig.parse(malformed);
        TestKit.check("valid lines survive invalid regex", !parsed.enabled
                && parsed.disabledIds.contains("trim-ends")
                && parsed.customRules.size() == 1
                && "good".equals(parsed.customRules.get(0).id));

        String rejected = "rule" + sep + "unknown" + sep + "TITLE" + sep + "x" + sep + "y\n"
                + "rule" + sep + "duplicate" + sep + "ALL" + sep + "a" + sep + "b\n"
                + "rule" + sep + "duplicate" + sep + "ALL" + sep + "c" + sep + "d\n"
                + "rule" + sep + "album-ep" + sep + "ALL" + sep + "e" + sep + "f\n"
                + "rule" + sep + "short" + sep + "ALL" + sep + "x";
        parsed = SanitizeConfig.parse(rejected);
        TestKit.eq("invalid and duplicate rules rejected", Integer.valueOf(1),
                Integer.valueOf(parsed.customRules.size()));
        TestKit.eq("first duplicate id retained", "duplicate", parsed.customRules.get(0).id);

        assertDefaults("null parses as defaults", SanitizeConfig.parse(null));
        assertDefaults("empty parses as defaults", SanitizeConfig.parse(""));

        List<SanitizeRule> ordered = Arrays.asList(new SanitizeRule(
                "after-trim", SanitizeRule.Scope.ALL, "^A$", "changed", true, ""));
        SanitizeConfig config = new SanitizeConfig(true, new HashSet<String>(), ordered);
        TestKit.eq("custom rule runs after defaults", "changed",
                Sanitizer.from(config).apply(SanitizeRule.Scope.TRACK, "  A  "));
        TestKit.eq("disabled sanitizer preserves raw value", "  A  ",
                Sanitizer.from(config.withEnabled(false)).apply(
                        SanitizeRule.Scope.TRACK, "  A  "));
    }

    private static void roundTrip(boolean enabled) {
        Set<String> disabled = new HashSet<String>(Arrays.asList("album-ep", "custom-two"));
        List<SanitizeRule> rules = new ArrayList<SanitizeRule>();
        rules.add(new SanitizeRule("custom-one", SanitizeRule.Scope.ARTIST,
                "feat\\.", "featuring", true, ""));
        rules.add(new SanitizeRule("custom-two", SanitizeRule.Scope.ALBUM_ARTIST,
                "\\s+", " ", true, ""));
        SanitizeConfig original = new SanitizeConfig(enabled, disabled, rules);
        SanitizeConfig parsed = SanitizeConfig.parse(original.serialize());
        TestKit.eq("round-trip enabled " + enabled, Boolean.valueOf(enabled),
                Boolean.valueOf(parsed.enabled));
        TestKit.eq("round-trip disabled " + enabled, disabled, parsed.disabledIds);
        TestKit.eq("round-trip custom count " + enabled, Integer.valueOf(rules.size()),
                Integer.valueOf(parsed.customRules.size()));
        for (int i = 0; i < rules.size(); i++) {
            SanitizeRule expected = rules.get(i);
            SanitizeRule actual = parsed.customRules.get(i);
            TestKit.eq("round-trip id " + i + " " + enabled, expected.id, actual.id);
            TestKit.eq("round-trip scope " + i + " " + enabled, expected.scope, actual.scope);
            TestKit.eq("round-trip regex " + i + " " + enabled,
                    expected.pattern.pattern(), actual.pattern.pattern());
            TestKit.eq("round-trip replacement " + i + " " + enabled,
                    expected.replacement, actual.replacement);
        }
    }

    private static void assertDefaults(String label, SanitizeConfig config) {
        SanitizeConfig defaults = SanitizeConfig.defaults();
        TestKit.check(label, config.enabled == defaults.enabled
                && config.disabledIds.equals(defaults.disabledIds)
                && config.customRules.isEmpty());
    }

    /** 区切り文字と行区切りが本文に現れてもラウンドトリップが壊れないこと。 */
    static void runEscaping() {
        TestKit.section("SanitizeConfig escaping");
        check("replacement newline", "nl", SanitizeRule.Scope.ALBUM, "X", "a\nb");
        check("regex newline", "nl2", SanitizeRule.Scope.ALBUM, "a\nb", "z");
        check("id newline", "a\nrule", SanitizeRule.Scope.ALL, "q", "");
        check("regex separator", "sep", SanitizeRule.Scope.ALBUM, "a\u001Fb", "z");
        check("replacement separator", "sep2", SanitizeRule.Scope.ALBUM, "X", "a\u001Fb");
        check("regex backslashes", "bs", SanitizeRule.Scope.ALBUM,
                "\\s+\\((Deluxe|Remaster)\\)$", "");
        check("replacement backslash", "bs2", SanitizeRule.Scope.ALL, "X", "a\\b");
        check("trailing backslash in replacement", "bs3", SanitizeRule.Scope.ALL, "X", "a\\");

        // 余分なフィールドを持つ壊れた rule 行は、黙って別のルールに化けず捨てられる。
        String broken = "enabled\u001F1\ndisabled\nrule\u001Fx\u001FALL\u001Fa\u001Fb\u001Fc";
        TestKit.eq("rule line with extra fields rejected",
                Integer.valueOf(0),
                Integer.valueOf(SanitizeConfig.parse(broken).customRules.size()));
    }

    private static void check(String label, String id, SanitizeRule.Scope scope,
            String regex, String replacement) {
        java.util.List<SanitizeRule> rules = new java.util.ArrayList<SanitizeRule>();
        rules.add(new SanitizeRule(id, scope, regex, replacement, true, ""));
        SanitizeConfig back = SanitizeConfig.parse(
                SanitizeConfig.defaults().withCustomRules(rules).serialize());
        TestKit.eq(label + " survives", Integer.valueOf(1),
                Integer.valueOf(back.customRules.size()));
        if (back.customRules.size() != 1) return;
        SanitizeRule got = back.customRules.get(0);
        TestKit.eq(label + " id", id, got.id);
        TestKit.eq(label + " regex", regex, got.pattern.pattern());
        TestKit.eq(label + " replacement", replacement, got.replacement);
    }

    private SanitizeConfigTests() {}
}
