import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.runelite.mp.compat.jdk.JdkFiles;
import net.runelite.mp.compat.jdk.JdkRegex;
import net.runelite.mp.compat.jdk.JdkStrings;
import net.runelite.mp.compat.jdk.RuntimeVersion;

/**
 * The JdkRetargets shims against the real JDK they stand in for: every case runs both and
 * compares the result, or the exception class when the JDK throws. The shims are compiled with
 * --release 8 by run-runtime-contracts.py, this file with the host JDK (11+).
 */
public class TestJdkShimContracts {
    private static final String[] STRINGS = {
        "", " ", "\t\n\u000B\f\r", "a", " a ", " a ", " a ", "　x　",
        "a​b", "😀 ", " 😀", "\uD800", " \uDC00 ", "\u001Cx\u001F", "abc",
        "a\nb", "a\r\nb", "a\rb", "a\n", "a\r\n", "\n", "\r\n\r\n", "\n\na", "a\n\n", "\r\r\n\n", "x\u0085y",
        "x y", "\u0000 \u0000",
    };
    private static int cases;
    private static int failures;

    public static void main(String[] args) throws Exception {
        for (String s : STRINGS) {
            check("strip " + show(s), () -> s.strip(), () -> JdkStrings.strip(s));
            check("stripLeading " + show(s), () -> s.stripLeading(), () -> JdkStrings.stripLeading(s));
            check("stripTrailing " + show(s), () -> s.stripTrailing(), () -> JdkStrings.stripTrailing(s));
            check("isBlank " + show(s), () -> s.isBlank(), () -> JdkStrings.isBlank(s));
            check("lines " + show(s), () -> s.lines().collect(Collectors.toList()),
                    () -> JdkStrings.lines(s).collect(Collectors.toList()));
            for (int count : new int[] {-1, 0, 1, 2, 7}) {
                check("repeat " + show(s) + " " + count, () -> s.repeat(count), () -> JdkStrings.repeat(s, count));
            }
        }
        check("repeat overflow", () -> "ab".repeat(Integer.MAX_VALUE), () -> JdkStrings.repeat("ab", Integer.MAX_VALUE));
        check("repeat empty huge", () -> "".repeat(Integer.MAX_VALUE), () -> JdkStrings.repeat("", Integer.MAX_VALUE));
        check("repeat identity", () -> "q".repeat(1) == "q", () -> JdkStrings.repeat("q", 1) == "q");
        check("strip identity", () -> "q".strip() == "q", () -> JdkStrings.strip("q") == "q");
        String none = null;
        check("strip null", () -> none.strip(), () -> JdkStrings.strip(none));
        check("repeat null", () -> none.repeat(0), () -> JdkStrings.repeat(none, 0));
        check("lines null", () -> none.lines(), () -> JdkStrings.lines(none));
        check("isBlank null", () -> none.isBlank(), () -> JdkStrings.isBlank(none));

        Pattern word = Pattern.compile("(\\w)(\\w*)");
        String text = "one two  three $x";
        check("replaceAll fn", () -> word.matcher(text).replaceAll(r -> r.group(2) + r.group(1)),
                () -> JdkRegex.replaceAll(word.matcher(text), r -> r.group(2) + r.group(1)));
        check("replaceAll group ref", () -> word.matcher(text).replaceAll(r -> "<$1>"),
                () -> JdkRegex.replaceAll(word.matcher(text), r -> "<$1>"));
        check("replaceAll bad ref", () -> word.matcher(text).replaceAll(r -> "$9"),
                () -> JdkRegex.replaceAll(word.matcher(text), r -> "$9"));
        check("replaceAll null", () -> word.matcher(text).replaceAll((java.util.function.Function<MatchResult, String>) null),
                () -> JdkRegex.replaceAll(word.matcher(text), null));
        check("replaceFirst fn", () -> word.matcher(text).replaceFirst(r -> r.group().toUpperCase()),
                () -> JdkRegex.replaceFirst(word.matcher(text), r -> r.group().toUpperCase()));
        check("replaceAll after find", () -> { Matcher m = word.matcher(text); m.find(); return m.replaceAll(r -> "_"); },
                () -> { Matcher m = word.matcher(text); m.find(); return JdkRegex.replaceAll(m, r -> "_"); });
        check("results", () -> word.matcher(text).results().map(MatchResult::group).collect(Collectors.toList()),
                () -> JdkRegex.results(word.matcher(text)).map(MatchResult::group).collect(Collectors.toList()));
        check("append", () -> {
            Matcher m = word.matcher(text);
            StringBuilder sb = new StringBuilder(">");
            while (m.find()) {
                m.appendReplacement(sb, "[$2]");
            }
            return m.appendTail(sb).toString();
        }, () -> {
            Matcher m = word.matcher(text);
            StringBuilder sb = new StringBuilder(">");
            while (m.find()) {
                JdkRegex.appendReplacement(m, sb, "[$2]");
            }
            return JdkRegex.appendTail(m, sb).toString();
        });

        Path dir = Files.createTempDirectory("jdk-shims");
        try {
            Path real = dir.resolve("real.txt");
            Path shim = dir.resolve("shim.txt");
            String content = "café 😀\n";
            check("writeString", () -> Files.writeString(real, content).equals(real),
                    () -> JdkFiles.writeString(shim, content).equals(shim));
            check("written bytes", () -> new String(Files.readAllBytes(real), StandardCharsets.UTF_8),
                    () -> new String(Files.readAllBytes(shim), StandardCharsets.UTF_8));
            check("append option", () -> { Files.writeString(real, "+", StandardOpenOption.APPEND); return Files.readString(real); },
                    () -> { JdkFiles.writeString(shim, "+", StandardOpenOption.APPEND); return JdkFiles.readString(shim); });
            check("latin1", () -> { Files.writeString(real, content, StandardCharsets.ISO_8859_1); return null; },
                    () -> { JdkFiles.writeString(shim, content, StandardCharsets.ISO_8859_1); return null; });
            Files.write(real, new byte[] {(byte) 0xC3, (byte) 0x28});
            check("malformed", () -> Files.readString(real), () -> JdkFiles.readString(real));
            check("latin1 read", () -> Files.readString(real, StandardCharsets.ISO_8859_1),
                    () -> JdkFiles.readString(real, StandardCharsets.ISO_8859_1));
            check("missing", () -> Files.readString(dir.resolve("none")), () -> JdkFiles.readString(dir.resolve("none")));
        } finally {
            try (java.util.stream.Stream<Path> files = Files.list(dir)) {
                for (Path p : (Iterable<Path>) files::iterator) {
                    Files.delete(p);
                }
            }
            Files.delete(dir);
        }

        for (String v : new String[] {"9", "11.0.2", "17.0.10+7", "21-ea+5", "21.0.1+12-LTS", "1.8", "11.0.0", "x", ""}) {
            check("Version.parse " + v, () -> Runtime.Version.parse(v).toString(), () -> RuntimeVersion.parse(v).toString());
            check("Version.feature " + v, () -> Runtime.Version.parse(v).feature(), () -> RuntimeVersion.parse(v).feature());
            check("Version.pre " + v, () -> Runtime.Version.parse(v).pre(), () -> RuntimeVersion.parse(v).pre());
            check("Version.build " + v, () -> Runtime.Version.parse(v).build(), () -> RuntimeVersion.parse(v).build());
        }
        check("Version.compare", () -> Integer.signum(Runtime.Version.parse("11.0.2").compareTo(Runtime.Version.parse("11.0.10"))),
                () -> Integer.signum(RuntimeVersion.parse("11.0.2").compareTo(RuntimeVersion.parse("11.0.10"))));

        System.out.println(cases + " cases, " + failures + " failures");
        if (failures != 0) {
            throw new AssertionError(failures + " shim cases differ from the JDK");
        }
        System.out.println("PASS: TestJdkShimContracts");
    }

    private static void check(String label, Callable<Object> jdk, Callable<Object> shim) {
        cases++;
        String expected = outcome(jdk);
        String actual = outcome(shim);
        if (!Objects.equals(expected, actual)) {
            failures++;
            System.out.println("FAIL " + label + ": jdk=" + expected + " shim=" + actual);
        }
    }

    private static String outcome(Callable<Object> call) {
        try {
            return "= " + call.call();
        } catch (Throwable t) {
            return "! " + t.getClass().getName();
        }
    }

    private static String show(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            out.append(c >= 0x20 && c < 0x7F ? String.valueOf(c) : String.format("\\u%04X", (int) c));
        }
        return out.append('"').toString();
    }
}
