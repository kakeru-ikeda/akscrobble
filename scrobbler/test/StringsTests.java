import com.akscrobble.core.Strings;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StringsTests {
    private static final Pattern CONSTANT_NAME = Pattern.compile("[A-Z0-9_]+");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([0-9])\\}");

    static void run() {
        TestKit.section("Strings");
        String[] keys = Strings.keys();
        Set<String> tableKeys = new HashSet<String>();
        for (String key : keys) {
            TestKit.check("unique key " + key, tableKeys.add(key));
            String ja = Strings.get(Strings.Lang.JA, key);
            String en = Strings.get(Strings.Lang.EN, key);
            String ko = Strings.get(Strings.Lang.KO, key);
            TestKit.check(key + " has JA", ja != null && ja.length() > 0);
            TestKit.check(key + " has EN", en != null && en.length() > 0);
            TestKit.check(key + " has KO", ko != null && ko.length() > 0);
            TestKit.eq(key + " JA/EN placeholders", placeholders(ja), placeholders(en));
            TestKit.eq(key + " EN/KO placeholders", placeholders(en), placeholders(ko));
        }

        Set<String> constants = new HashSet<String>();
        try {
            for (Field field : Strings.class.getFields()) {
                int modifiers = field.getModifiers();
                if (field.getType() == String.class && Modifier.isStatic(modifiers)
                        && Modifier.isFinal(modifiers)
                        && CONSTANT_NAME.matcher(field.getName()).matches()) {
                    constants.add((String) field.get(null));
                }
            }
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
        TestKit.eq("constants match table keys", constants, tableKeys);
        TestKit.eq("unknown key", "missing", Strings.get(Strings.Lang.EN, "missing"));
        TestKit.eq("null key", null, Strings.get(Strings.Lang.EN, null));
        TestKit.eq("placeholder replacement", "Storage permission: read=true write=false",
                Strings.get(Strings.Lang.EN, Strings.MAIN_STORAGE_RESULT, true, false));
        TestKit.eq("missing argument is preserved", "Storage permission: read=true write={1}",
                Strings.get(Strings.Lang.EN, Strings.MAIN_STORAGE_RESULT, true));
        TestKit.eq("argument content is not re-scanned", "Storage permission: read={1} write=false",
                Strings.get(Strings.Lang.EN, Strings.MAIN_STORAGE_RESULT, "{1}", false));
        TestKit.eq("brace in argument survives", "Storage permission: read=a{1}b write=false",
                Strings.get(Strings.Lang.EN, Strings.MAIN_STORAGE_RESULT, "a{1}b", false));
        TestKit.eq("pick ja", Strings.Lang.JA, Strings.pick("ja"));
        TestKit.eq("pick ko", Strings.Lang.KO, Strings.pick("ko"));
        TestKit.eq("pick en", Strings.Lang.EN, Strings.pick("en"));
        TestKit.eq("pick null", Strings.Lang.EN, Strings.pick(null));
        TestKit.eq("pick uppercase JA", Strings.Lang.JA, Strings.pick("JA"));
    }

    private static Set<String> placeholders(String value) {
        Set<String> result = new HashSet<String>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) result.add(matcher.group(1));
        return result;
    }

    private StringsTests() {}
}
