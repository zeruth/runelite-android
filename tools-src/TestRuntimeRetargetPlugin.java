import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A hub plugin compiled with --release 11 that calls JdkRetargets members, directly and through
 * method references. test-runtime-plugin-dex.py retargets it with JdkRetargeter, dexes it against
 * the APK's compiler assets and runs it with the APK on the classpath; on API 26-33 every one
 * of these calls is a NoSuchMethodError without the retarget.
 */
public final class TestRuntimeRetargetPlugin {
    public static void main(String[] args) throws Exception {
        check("ab".repeat(3), "ababab");
        check("  x\t".strip(), "x");
        check(" x ".stripLeading(), "x ");
        check(" x ".stripTrailing(), " x");
        check("  \n".isBlank(), true);
        check("a\r\nb\rc\n".lines().collect(Collectors.toList()), List.of("a", "b", "c"));
        Function<String, String> strip = String::strip;
        check(strip.apply("\ty "), "y");

        Path file = Files.createTempFile("retarget", ".txt");
        try {
            Files.writeString(file, "café");
            check(Files.readString(file), "café");
        } finally {
            Files.delete(file);
        }

        Runtime.Version version = Runtime.version();
        if (version.feature() < 8) {
            throw new AssertionError("Runtime.version() " + version);
        }
        check(Runtime.Version.parse("17.0.2+8").update(), 2);

        ConcurrentHashMap<String, Boolean> map = new ConcurrentHashMap<>();
        ConcurrentHashMap.KeySetView<String, Boolean> keys = map.keySet(Boolean.TRUE);
        keys.add("k");
        check(map.keySet().contains("k"), true);

        // Hub jars move java.util.regex to the desktop engine, JDK 9 members included.
        Matcher matcher = Pattern.compile("(\\d+)").matcher("a1b22");
        check(matcher.replaceAll(r -> "<" + r.group(1) + ">"), "a<1>b<22>");
        check("a,b,,".split(","), 2);
        // String's own regex methods go to DesktopRegexStrings, the same engine; splitWithDelimiters
        // is JDK 21, so TestRuntimeRetarget covers it with a synthesized call instead.
        check("a1b2c3".split("\\d", 2), 2);
        check("a1b2c3".split("\\d", 2)[1], "b2c3");
        check("abc".matches("a.c"), true);
        check("a1b22".replaceAll("(\\d+)", "<$1>"), "a<1>b<22>");
        check("a1b22".replaceFirst("\\d+", "#"), "a#b22");
        BiFunction<String, String, Boolean> matches = String::matches;
        check(matches.apply("x y", "x\\Ry"), true);
        System.out.println("PASS: runtime plugin JDK retargets");
    }

    private static void check(Object actual, Object expected) {
        if (expected instanceof Integer && actual instanceof String[]) {
            actual = ((String[]) actual).length;
        }
        if (!actual.equals(expected)) {
            throw new AssertionError(actual + " != " + expected);
        }
    }
}
