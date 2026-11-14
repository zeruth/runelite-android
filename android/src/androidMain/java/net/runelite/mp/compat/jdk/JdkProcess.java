package net.runelite.mp.compat.jdk;

import java.util.Optional;

/**
 * Guard on {@code ProcessHandle.Info#command()}. The game client feeds the command into its
 * login machine-info packet via {@code xi.cq}, which throws {@link IllegalArgumentException} on
 * any string containing a NUL ("error_game_crash" the moment login is hit). An earlier
 * {@code ProcessHandle} returned the raw NUL-separated {@code /proc/PID/cmdline}; the android-awt
 * shadow now answers the {@code /proc/self/exe} path, so this only truncates at a NUL should a
 * future source bring one back.
 *
 * <p>The receiver is typed {@code Object}, so the retargeted call needs no cast and this class
 * no link to the shadow interface; the call goes through reflection.
 */
public final class JdkProcess {
    private JdkProcess() {
    }

    public static Optional<String> command(Object info) {
        if (info == null) {
            throw new NullPointerException();
        }
        Object result;
        try {
            // Through the public interface: the implementation class (ProcessHandleImpl$Info on a
            // JDK) is package-private, and invoking its method object would be refused.
            result = Class.forName("java.lang.ProcessHandle$Info").getMethod("command").invoke(info);
        } catch (Throwable t) {
            return Optional.empty();
        }
        if (!(result instanceof Optional) || !((Optional<?>) result).isPresent()) {
            return Optional.empty();
        }
        Object value = ((Optional<?>) result).get();
        if (!(value instanceof String)) {
            return Optional.empty();
        }
        String command = (String) value;
        int nul = command.indexOf(0);
        return Optional.of(nul < 0 ? command : command.substring(0, nul));
    }
}
