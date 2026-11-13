package net.runelite.mp.compat.jdk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.stream.Stream;

/**
 * JDK 9 {@code java.util.regex.Matcher} members, for the injected client (hub jars move to the
 * desktop engine in {@code net.runelite.mp.compat.regex} instead, which has them natively).
 * Built on the {@code StringBuffer} forms ART has had since API 1.
 */
public final class JdkRegex {
    private JdkRegex() {
    }

    public static Matcher appendReplacement(Matcher matcher, StringBuilder sb, String replacement) {
        StringBuffer out = new StringBuffer();
        matcher.appendReplacement(out, replacement);
        sb.append(out);
        return matcher;
    }

    public static StringBuilder appendTail(Matcher matcher, StringBuilder sb) {
        StringBuffer out = new StringBuffer();
        matcher.appendTail(out);
        sb.append(out);
        return sb;
    }

    /**
     * As the JDK: resets first, hands the matcher itself to the replacer, and expands
     * {@code $n}/{@code \} in what it returns.
     */
    public static String replaceAll(Matcher matcher, Function<MatchResult, String> replacer) {
        if (replacer == null) {
            throw new NullPointerException();
        }
        matcher.reset();
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(out, replacer.apply(matcher));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static String replaceFirst(Matcher matcher, Function<MatchResult, String> replacer) {
        if (replacer == null) {
            throw new NullPointerException();
        }
        matcher.reset();
        StringBuffer out = new StringBuffer();
        if (matcher.find()) {
            matcher.appendReplacement(out, replacer.apply(matcher));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Collected eagerly from the matcher's current position; the JDK's stream is lazy. */
    public static Stream<MatchResult> results(Matcher matcher) {
        List<MatchResult> results = new ArrayList<MatchResult>();
        while (matcher.find()) {
            results.add(matcher.toMatchResult());
        }
        return results.stream();
    }
}
