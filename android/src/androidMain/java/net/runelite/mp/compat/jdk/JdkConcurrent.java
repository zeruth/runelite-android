package net.runelite.mp.compat.jdk;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code ConcurrentHashMap.keySet()} with the JDK 8 return type {@code KeySetView}. Android's
 * {@code android.jar} declares only {@code keySet()Ljava/util/Set;} (libcore kept the Java 7
 * descriptor for binary compatibility), so desktop-compiled callers fail with
 * {@code NoSuchMethodError}. The object libcore returns is a {@code KeySetView}; the call goes
 * through {@link Map} so it links against the descriptor every runtime has.
 */
public final class JdkConcurrent {
    private JdkConcurrent() {
    }

    public static <K, V> ConcurrentHashMap.KeySetView<K, V> keySet(ConcurrentHashMap<K, V> map) {
        Map<K, V> plain = map;
        @SuppressWarnings("unchecked")
        ConcurrentHashMap.KeySetView<K, V> view = (ConcurrentHashMap.KeySetView<K, V>) plain.keySet();
        return view;
    }
}
