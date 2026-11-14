import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Runs against the packaged RuneLite classes, not copies of their source. */
public final class TestBundledClientContracts {
    private static int checks;
    private static void check(boolean result, String name) {
        if (!result) throw new AssertionError(name);
        checks++;
    }

    public static void main(String[] args) {
        try { run(args); }
        catch (Throwable error) { error.printStackTrace(System.out); System.exit(1); }
    }

    private static void run(String[] args) throws Exception {
        File directory = new File(args[0]);
        if (!directory.isDirectory()) throw new IllegalArgumentException("Existing test directory required");
        System.setProperty("user.home", directory.getAbsolutePath());
        System.setProperty("java.io.tmpdir", directory.getAbsolutePath());
        Class<?> type = Class.forName("net.runelite.client.config.ConfigData");
        Constructor<?> constructor = type.getDeclaredConstructor(File.class);
        constructor.setAccessible(true);
        File missing = new File(directory, "missing-config.properties");
        if (missing.exists()) throw new IllegalArgumentException("Fixture path must be absent");
        Object data = constructor.newInstance(missing);
        Method set = type.getDeclaredMethod("setProperty", String.class, String.class);
        Method keys = type.getDeclaredMethod("keySet");
        Method unset = type.getDeclaredMethod("unset", String.class);
        Field storage = type.getDeclaredField("properties");
        set.setAccessible(true); keys.setAccessible(true); unset.setAccessible(true); storage.setAccessible(true);
        check(storage.get(data) instanceof ConcurrentHashMap, "concurrent storage preserved");
        set.invoke(data, "one", "a");
        Set<?> live = (Set<?>) keys.invoke(data);
        check(live.contains("one"), "configuration keys available");
        set.invoke(data, "two", "b");
        check(live.contains("two"), "live key view receives additions");
        unset.invoke(data, "one");
        check(!live.contains("one"), "live key view receives removals");
        check(!missing.exists(), "key access did not write configuration");

        Class<?> text = Class.forName("net.runelite.client.util.Text");
        Method unescape = text.getMethod("unescapeJagex", String.class);
        Method formatting = text.getMethod("removeFormattingTags", String.class);
        String input = "A<lt>B<gt><at><nbh><col=ff00>日本</col>";
        check("A<B>@-日本".equals(unescape.invoke(null, input)), "functional regex replacement");
        check("A<lt>B<gt><at><nbh>日本".equals(formatting.invoke(null, input)), "StringBuilder regex replacement and tail");
        check("$1\\x".equals(unescape.invoke(null, "$1\\x")), "literal dollar and backslash preserved");
        Class<?> wildcard = Class.forName("net.runelite.client.util.WildcardMatcher");
        Method matches = wildcard.getMethod("matches", String.class, String.class);
        check((Boolean) matches.invoke(null, "*rune*", "RUNE platebody"), "case-insensitive wildcard search");
        check(!(Boolean) matches.invoke(null, "*rune*", "Dragon platebody"), "wildcard rejects unmatched item");
        check((Boolean) matches.invoke(null, "A[$]\\*", "A[$]\\item"), "wildcard escapes regex punctuation");
        System.out.println("PASS TestBundledClientContracts " + checks + " checks against packaged classes");
    }
}
