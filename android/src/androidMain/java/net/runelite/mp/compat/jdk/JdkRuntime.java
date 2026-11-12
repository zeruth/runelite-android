package net.runelite.mp.compat.jdk;

import java.util.Collections;
import java.util.Optional;

/** {@code Runtime.version()} (JDK 9), answering the {@link RuntimeVersion} stand-in. */
public final class JdkRuntime {
    private static volatile RuntimeVersion current;

    private JdkRuntime() {
    }

    public static RuntimeVersion version() {
        RuntimeVersion version = current;
        if (version == null) {
            version = current();
            current = version;
        }
        return version;
    }

    /**
     * A JDK 9+ host reports its own version string. ART reports "0.9" and a JDK 8 host "1.8.0_x",
     * neither of which has a {@code Runtime.version()}: those answer feature 8, the libcore API
     * level at the app's minSdk, so plugins gating on a newer feature keep their JDK 8 path.
     */
    private static RuntimeVersion current() {
        String[] properties = {"java.runtime.version", "java.version", "java.specification.version"};
        for (String property : properties) {
            String value = System.getProperty(property);
            if (value == null) {
                continue;
            }
            try {
                RuntimeVersion parsed = RuntimeVersion.parse(value);
                if (parsed.feature() >= 9) {
                    return parsed;
                }
            } catch (IllegalArgumentException ignored) {
                // not a JDK 9+ version string; try the next property
            }
        }
        return new RuntimeVersion(Collections.singletonList(8), Optional.<String>empty(),
                Optional.<Integer>empty(), Optional.<String>empty());
    }
}
