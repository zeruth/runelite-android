package net.runelite.mp.util;

/**
 * Runtime helper that {@code DesugarStringConcatTask} rewrites
 * {@code invokedynamic makeConcatWithConstants} sites into. Plain Java 8 bytecode, no
 * invokedynamic — survives D8 untouched. JDK member polyfills live in
 * {@code net.runelite.mp.compat.jdk}, driven by the {@code JdkRetargets} table.
 */
public final class IndyConcat {
    private IndyConcat() {}

    /**
     * @param recipe the recipe string from the original bootstrap (with {@code } =
     *               next runtime arg, {@code } = next bootstrap-time constant).
     * @param args   the runtime arguments to the indy, boxed in order.
     * @param consts the bootstrap-time constants (positions 1..n of the original BSM args).
     */
    public static String concat(String recipe, Object[] args, Object[] consts) {
        StringBuilder out = new StringBuilder();
        StringBuilder literal = new StringBuilder();
        int argIdx = 0;
        int constIdx = 0;
        int len = recipe.length();
        for (int i = 0; i < len; i++) {
            char c = recipe.charAt(i);
            if (c == '') {
                flush(out, literal);
                out.append(args[argIdx++]);
            } else if (c == '') {
                flush(out, literal);
                out.append(consts[constIdx++]);
            } else {
                literal.append(c);
            }
        }
        flush(out, literal);
        return out.toString();
    }

    private static void flush(StringBuilder out, StringBuilder literal) {
        if (literal.length() > 0) {
            out.append(literal);
            literal.setLength(0);
        }
    }
}
