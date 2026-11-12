package net.runelite.mp.util;

/**
 * Helpers used by the {@code desugarLambdas} bytecode pass to sanitize strings the
 * obfuscated client serializes into its network buffer. Specifically, {@code xi.cq}
 * (the buffer's NUL-terminated-string writer) throws {@link IllegalArgumentException}
 * on any input containing a NUL char — manifesting as "error_game_crash" at login
 * when the machine-info packet picks up a NUL-laced string from Android-specific APIs
 * (ProcessHandle, /proc/PID/cmdline, etc.).
 *
 * <p>The desugar pass prepends a {@link #stripNul(String)} call at method entry of
 * every {@code (String, int) -> void} method whose body has the IAE-on-NUL check, so
 * the input is cleaned before the check fires. The rewrite is listed with the other
 * java.lang shims in android-awt/PORTING.md, section 10.
 */
public final class StringShim {
    private StringShim() {
    }

    public static String stripNul(String s) {
        if (s == null) {
            return null;
        }
        int nul = s.indexOf(0);
        if (nul < 0) {
            return s;
        }
        // Diagnostic: when we actually do truncate, log the string + caller so we
        // can confirm whether the NUL is from ProcessHandle (expected/harmless) or
        // from some other Android-specific code path that's corrupting login data.
        try {
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            StringBuilder frames = new StringBuilder();
            int max = Math.min(st.length, 10);
            for (int i = 2; i < max; i++) {
                if (frames.length() > 0) frames.append(" <- ");
                frames.append(st[i]);
            }
            String safe = s.replace((char) 0, (char) 0xB7);
            android.util.Log.w("StringShim", "stripNul len=" + s.length() + " → " + nul +
                ", text=\"" + safe + "\"\n  stack: " + frames);
        } catch (Throwable ignored) {
        }
        return s.substring(0, nul);
    }
}
