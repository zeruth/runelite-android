package net.runelite.mp.util;

import java.util.Optional;

/**
 * Guard on {@code ProcessHandle.Info#command()} for the game client. The client feeds the
 * command into its login machine-info packet via {@code xi.cq}, which throws
 * {@link IllegalArgumentException} on any string containing a NUL, manifesting as
 * "error_game_crash" the moment the login button is hit. An earlier {@code ProcessHandle}
 * returned the raw NUL-separated {@code /proc/PID/cmdline}; the android-awt shadow now
 * answers the {@code /proc/self/exe} path ({@code /system/bin/app_process64}), so this only
 * truncates at a NUL should a future source bring one back.
 *
 * <p>{@code DesugarLambdasTask} rewrites every {@code INVOKEINTERFACE ProcessHandle$Info.command()}
 * site of the injected client to an {@code INVOKESTATIC} call here with descriptor
 * {@code (Ljava/lang/Object;)Ljava/util/Optional;}; the receiver passes through as
 * {@code Object}, so the rewrite needs no cast and the shim no link to the shadow class
 * (see android-awt/PORTING.md, section 10).
 */
public final class ProcessHandleShim {
    private ProcessHandleShim() {
    }

    public static Optional<String> sanitizedCommand(Object info) {
        if (info == null) {
            return Optional.empty();
        }
        Optional<?> raw;
        try {
            Object result = info.getClass().getMethod("command").invoke(info);
            if (!(result instanceof Optional)) {
                return Optional.empty();
            }
            raw = (Optional<?>) result;
        } catch (Throwable t) {
            return Optional.empty();
        }
        if (!raw.isPresent()) {
            return Optional.empty();
        }
        Object value = raw.get();
        if (!(value instanceof String)) {
            return Optional.empty();
        }
        String s = (String) value;
        int nul = s.indexOf(0);
        if (nul < 0) {
            return Optional.of(s);
        }
        return Optional.of(s.substring(0, nul));
    }
}
