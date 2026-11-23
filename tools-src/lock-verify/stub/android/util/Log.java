package android.util;

/** JVM stand-in for android.util.Log so the on-device transforms run in the harness. */
public final class Log {
    public static int i(String t, String m) { System.err.println("I " + t + ": " + m); return 0; }
    public static int w(String t, String m) { System.err.println("W " + t + ": " + m); return 0; }
    public static int d(String t, String m) { return 0; }
}
