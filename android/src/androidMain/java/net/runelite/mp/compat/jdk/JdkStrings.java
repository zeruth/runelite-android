package net.runelite.mp.compat.jdk;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * JDK 11 {@code String} members for bytecode compiled against a newer JDK. {@link JdkRetargets}
 * turns {@code INVOKEVIRTUAL java/lang/String.<member>} into a static call here with the
 * receiver first, so the semantics, null receiver included, are the JDK's whatever D8's own
 * backports or the device's libcore do. JDK 8 code only: the host contract compiles this
 * file with {@code --release 8} and compares it against the real JDK.
 */
public final class JdkStrings {
    private static final Logger LOG = Logger.getLogger("JdkStrings");

    private JdkStrings() {
    }

    /** {@code String.repeat(int)}, JDK 11. */
    public static String repeat(String s, int count) {
        int length = s.length();
        if (count < 0) {
            throw new IllegalArgumentException("count is negative: " + count);
        }
        if (count == 1) {
            return s;
        }
        if (length == 0 || count == 0) {
            return "";
        }
        if (length > Integer.MAX_VALUE / count) {
            throw new OutOfMemoryError("Repeating " + length + " chars String " + count
                    + " times will produce a String exceeding maximum size.");
        }
        StringBuilder out = new StringBuilder(length * count);
        for (int i = 0; i < count; i++) {
            out.append(s);
        }
        return out.toString();
    }

    /** {@code String.strip()}, JDK 11: {@link Character#isWhitespace(int)} on both ends. */
    public static String strip(String s) {
        int start = leadingEnd(s);
        if (start == s.length()) {
            return "";
        }
        int end = trailingStart(s);
        return start == 0 && end == s.length() ? s : s.substring(start, end);
    }

    /** {@code String.stripLeading()}, JDK 11. */
    public static String stripLeading(String s) {
        int start = leadingEnd(s);
        return start == 0 ? s : s.substring(start);
    }

    /** {@code String.stripTrailing()}, JDK 11. */
    public static String stripTrailing(String s) {
        int end = trailingStart(s);
        return end == s.length() ? s : s.substring(0, end);
    }

    /** {@code String.isBlank()}, JDK 11. */
    public static boolean isBlank(String s) {
        return leadingEnd(s) == s.length();
    }

    /**
     * {@code String.lines()}, JDK 11: split at {@code \n}, {@code \r} and {@code \r\n}, no
     * trailing empty line. Collected eagerly; the lines are the JDK's, only the laziness differs.
     */
    public static Stream<String> lines(String s) {
        int length = s.length();
        List<String> lines = new ArrayList<String>();
        int start = 0;
        int i = 0;
        while (i < length) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') {
                lines.add(s.substring(start, i));
                i += c == '\r' && i + 1 < length && s.charAt(i + 1) == '\n' ? 2 : 1;
                start = i;
            } else {
                i++;
            }
        }
        if (start < length) {
            lines.add(s.substring(start));
        }
        return lines.stream();
    }

    /**
     * Cuts a string at its first NUL. Not a JDK member: {@code DesugarLambdasTask} prepends a call
     * to every method of the injected client shaped like its NUL-terminated string writer
     * ({@code xi.cq}: {@code (String, int) -> void}, calls {@code indexOf(I)}, throws
     * {@code IllegalArgumentException}), which would otherwise fail login with "error_game_crash"
     * on a NUL an Android API slipped into the machine-info packet (android-awt/PORTING.md,
     * section 10). A pattern prologue, not a member retarget, so it is not in the table.
     */
    public static String stripNul(String s) {
        if (s == null) {
            return null;
        }
        int nul = s.indexOf(0);
        if (nul < 0) {
            return s;
        }
        // A truncation means some source still leaks NULs into login data; say which.
        if (LOG.isLoggable(Level.WARNING)) {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            StringBuilder frames = new StringBuilder();
            for (int i = 2; i < Math.min(stack.length, 10); i++) {
                if (frames.length() > 0) {
                    frames.append(" <- ");
                }
                frames.append(stack[i]);
            }
            LOG.warning("stripNul len=" + s.length() + " -> " + nul + ", text=\""
                    + s.replace((char) 0, (char) 0xB7) + "\"\n  stack: " + frames);
        }
        return s.substring(0, nul);
    }

    private static int leadingEnd(String s) {
        int length = s.length();
        int i = 0;
        while (i < length) {
            int codePoint = s.codePointAt(i);
            if (!Character.isWhitespace(codePoint)) {
                break;
            }
            i += Character.charCount(codePoint);
        }
        return i;
    }

    private static int trailingStart(String s) {
        int i = s.length();
        while (i > 0) {
            int codePoint = s.codePointBefore(i);
            if (!Character.isWhitespace(codePoint)) {
                break;
            }
            i -= Character.charCount(codePoint);
        }
        return i;
    }
}
