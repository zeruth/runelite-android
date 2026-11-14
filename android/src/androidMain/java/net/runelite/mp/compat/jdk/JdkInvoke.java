package net.runelite.mp.compat.jdk;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/** {@code MethodHandles.privateLookupIn} (JDK 9; ART from API 33). */
public final class JdkInvoke {
    // The mode word IndyConcat.privateLookupInJava8 passed since the client first ran on
    // Android; RuneLite's event bus has been resolving its subscribers through it, so it stays.
    private static final int ALL_MODES = 0x40;

    private JdkInvoke() {
    }

    public static MethodHandles.Lookup privateLookupIn(Class<?> targetClass, MethodHandles.Lookup caller)
            throws IllegalAccessException {
        if (targetClass == null || caller == null) {
            throw new NullPointerException();
        }
        try {
            return (MethodHandles.Lookup) MethodHandles.class
                    .getMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class)
                    .invoke(null, targetClass, caller);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalAccessException) {
                throw (IllegalAccessException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException(cause);
        } catch (NoSuchMethodException e) {
            // Below API 33: the full-privilege lookup ART's own Lookup.in() builds from.
            return newLookup(targetClass);
        }
    }

    private static MethodHandles.Lookup newLookup(Class<?> targetClass) throws IllegalAccessException {
        try {
            Constructor<MethodHandles.Lookup> constructor =
                    MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(targetClass, ALL_MODES);
        } catch (ReflectiveOperationException e) {
            IllegalAccessException failure = new IllegalAccessException("privateLookupIn " + targetClass.getName());
            failure.initCause(e);
            throw failure;
        }
    }
}
