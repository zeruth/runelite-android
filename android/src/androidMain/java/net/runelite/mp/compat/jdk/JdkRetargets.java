package net.runelite.mp.compat.jdk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JDK members android.jar lacks at minSdk 26 that desktop-compiled bytecode is retargeted
 * away from, and the static shim each call becomes. {@code net.runelite.mp.compat.JdkRetargeter}
 * applies it to the injected client ({@code ClientTransforms}) and to hub jars
 * ({@code PluginDexer}); an instance member becomes a static call with the receiver first, so
 * the operand stack is unchanged. The rows are the {@code retarget} disposition of
 * docs/jdk-api-gaps.json plus the String members whose JDK semantics the host contract pins;
 * every other gap there is left to D8's backports and desugar_jdk_libs.
 *
 * <p>The {@code jdkRetargetCheck} task in android/build.gradle.kts reads the rows from this
 * source, one {@code onStatic}/{@code onVirtual}/{@code onInterface} call per line with the
 * owner, name and descriptor as its first three literals, and fails when android-awt or the app
 * itself calls one of them (nothing retargets their bytecode). Keep that shape.
 */
public final class JdkRetargets {
    public static final int STATIC = 0;
    public static final int VIRTUAL = 1;
    public static final int INTERFACE = 2;

    private static final String PKG = "net/runelite/mp/compat/jdk/";
    private static final String STRINGS = PKG + "JdkStrings";
    private static final String FILES = PKG + "JdkFiles";
    private static final String RUNTIME = PKG + "JdkRuntime";
    private static final String CONCURRENT = PKG + "JdkConcurrent";
    private static final String REGEX = PKG + "JdkRegex";
    private static final String INVOKE = PKG + "JdkInvoke";
    private static final String PROCESS = PKG + "JdkProcess";

    /** One table row: a JDK member and the static shim that replaces calls to it. */
    public static final class Entry {
        public final int kind;
        public final String owner;
        public final String name;
        public final String descriptor;
        public final String shimOwner;
        public final String shimName;
        public final String shimDescriptor;

        Entry(int kind, String owner, String name, String descriptor,
              String shimOwner, String shimName, String shimDescriptor) {
            this.kind = kind;
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
            this.shimOwner = shimOwner;
            this.shimName = shimName;
            this.shimDescriptor = shimDescriptor;
        }

        @Override
        public String toString() {
            return owner + "." + name + descriptor + " -> " + shimOwner + "." + shimName + shimDescriptor;
        }
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<String, Entry>();
    private static final Map<String, String> RELOCATED = new HashMap<String, String>();

    static {
        // String, JDK 11. D8 backports some of these too; the table makes the JDK's semantics
        // the only ones a retargeted call can see.
        onVirtual("java/lang/String", "repeat", "(I)Ljava/lang/String;", STRINGS);
        onVirtual("java/lang/String", "strip", "()Ljava/lang/String;", STRINGS);
        onVirtual("java/lang/String", "stripLeading", "()Ljava/lang/String;", STRINGS);
        onVirtual("java/lang/String", "stripTrailing", "()Ljava/lang/String;", STRINGS);
        onVirtual("java/lang/String", "isBlank", "()Z", STRINGS);
        onVirtual("java/lang/String", "lines", "()Ljava/util/stream/Stream;", STRINGS);

        // Files, JDK 11; Android has them from API 36.1, desugar_jdk_libs not at all.
        onStatic("java/nio/file/Files", "readString", "(Ljava/nio/file/Path;)Ljava/lang/String;", FILES);
        onStatic("java/nio/file/Files", "readString", "(Ljava/nio/file/Path;Ljava/nio/charset/Charset;)Ljava/lang/String;", FILES);
        onStatic("java/nio/file/Files", "writeString", "(Ljava/nio/file/Path;Ljava/lang/CharSequence;[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;", FILES);
        onStatic("java/nio/file/Files", "writeString", "(Ljava/nio/file/Path;Ljava/lang/CharSequence;Ljava/nio/charset/Charset;[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;", FILES);

        // Runtime.Version, JDK 9: the type itself moves to RuntimeVersion (see RELOCATED), so
        // its own members need no rows; only the static entry point does.
        onStatic("java/lang/Runtime", "version", "()Ljava/lang/Runtime$Version;", RUNTIME, "version", "()L" + PKG + "RuntimeVersion;");

        // ConcurrentHashMap.keySet() with the JDK 8 KeySetView return type (android.jar keeps Set).
        onVirtual("java/util/concurrent/ConcurrentHashMap", "keySet", "()Ljava/util/concurrent/ConcurrentHashMap$KeySetView;", CONCURRENT);

        // Matcher, JDK 9 (API 34). Hub jars relocate java.util.regex wholesale instead, so the
        // retargeter skips these rows when it relocates regex.
        onVirtual("java/util/regex/Matcher", "appendReplacement", "(Ljava/lang/StringBuilder;Ljava/lang/String;)Ljava/util/regex/Matcher;", REGEX);
        onVirtual("java/util/regex/Matcher", "appendTail", "(Ljava/lang/StringBuilder;)Ljava/lang/StringBuilder;", REGEX);
        onVirtual("java/util/regex/Matcher", "replaceAll", "(Ljava/util/function/Function;)Ljava/lang/String;", REGEX);
        onVirtual("java/util/regex/Matcher", "replaceFirst", "(Ljava/util/function/Function;)Ljava/lang/String;", REGEX);
        onVirtual("java/util/regex/Matcher", "results", "()Ljava/util/stream/Stream;", REGEX);

        // MethodHandles.privateLookupIn, JDK 9 (API 33).
        onStatic("java/lang/invoke/MethodHandles", "privateLookupIn", "(Ljava/lang/Class;Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/invoke/MethodHandles$Lookup;", INVOKE);

        // ProcessHandle.Info.command(): the login packet's NUL guard; the receiver passes as Object.
        onInterface("java/lang/ProcessHandle$Info", "command", "()Ljava/util/Optional;", PROCESS, "command", "(Ljava/lang/Object;)Ljava/util/Optional;");

        relocate("java/lang/Runtime$Version", PKG + "RuntimeVersion");
    }

    private JdkRetargets() {
    }

    /** The row for a call site, or null; {@code kind} must match the call's dispatch. */
    public static Entry find(int kind, String owner, String name, String descriptor) {
        Entry entry = ENTRIES.get(owner + '.' + name + descriptor);
        return entry != null && entry.kind == kind ? entry : null;
    }

    public static List<Entry> entries() {
        return Collections.unmodifiableList(new ArrayList<Entry>(ENTRIES.values()));
    }

    /** JDK types replaced wholesale: internal name to the app class standing in for it. */
    public static Map<String, String> relocatedTypes() {
        return Collections.unmodifiableMap(RELOCATED);
    }

    private static void onStatic(String owner, String name, String descriptor, String shimOwner) {
        add(STATIC, owner, name, descriptor, shimOwner, name, descriptor);
    }

    private static void onStatic(String owner, String name, String descriptor, String shimOwner,
                                 String shimName, String shimDescriptor) {
        add(STATIC, owner, name, descriptor, shimOwner, shimName, shimDescriptor);
    }

    private static void onVirtual(String owner, String name, String descriptor, String shimOwner) {
        add(VIRTUAL, owner, name, descriptor, shimOwner, name, receiverFirst(owner, descriptor));
    }

    private static void onInterface(String owner, String name, String descriptor, String shimOwner,
                                    String shimName, String shimDescriptor) {
        add(INTERFACE, owner, name, descriptor, shimOwner, shimName, shimDescriptor);
    }

    private static String receiverFirst(String owner, String descriptor) {
        return "(L" + owner + ";" + descriptor.substring(1);
    }

    private static void add(int kind, String owner, String name, String descriptor,
                            String shimOwner, String shimName, String shimDescriptor) {
        ENTRIES.put(owner + '.' + name + descriptor,
                new Entry(kind, owner, name, descriptor, shimOwner, shimName, shimDescriptor));
    }

    private static void relocate(String type, String replacement) {
        RELOCATED.put(type, replacement);
    }
}
