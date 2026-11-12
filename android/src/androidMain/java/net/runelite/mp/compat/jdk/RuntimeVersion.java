package net.runelite.mp.compat.jdk;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code java.lang.Runtime.Version} (JDK 9), which ART does not have. {@link JdkRetargets}
 * relocates the type, so retargeted bytecode calls these members under the JDK's names and
 * descriptors with this class in place of {@code Runtime$Version}. Parsing, ordering, equality
 * and {@code toString} follow OpenJDK's {@code Runtime.Version}.
 */
public final class RuntimeVersion implements Comparable<RuntimeVersion> {
    // OpenJDK's $VNUM(-$PRE)?(\+($BUILD)?(-$OPT)?)? with numbered groups (named groups are API 26).
    private static final Pattern FORMAT = Pattern.compile(
            "([1-9][0-9]*(?:(?:\\.0)*\\.[1-9][0-9]*)*)"
                    + "(?:-([a-zA-Z0-9]+))?"
                    + "(?:(\\+)(0|[1-9][0-9]*)?)?"
                    + "(?:-([-a-zA-Z0-9.]+))?");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private final List<Integer> version;
    private final Optional<String> pre;
    private final Optional<Integer> build;
    private final Optional<String> optional;

    RuntimeVersion(List<Integer> version, Optional<String> pre, Optional<Integer> build, Optional<String> optional) {
        this.version = Collections.unmodifiableList(new ArrayList<Integer>(version));
        this.pre = pre;
        this.build = build;
        this.optional = optional;
    }

    public static RuntimeVersion parse(String s) {
        if (s == null) {
            throw new NullPointerException();
        }
        if (s.isEmpty()) {
            // The JDK's all-digits fast path takes "" and fails in Integer.parseInt.
            throw new NumberFormatException("For input string: \"\"");
        }
        Matcher m = FORMAT.matcher(s);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid version string: '" + s + "'");
        }
        List<Integer> version = new ArrayList<Integer>();
        for (String part : m.group(1).split("\\.")) {
            version.add(Integer.parseInt(part));
        }
        Optional<String> pre = Optional.ofNullable(m.group(2));
        String buildText = m.group(4);
        Optional<Integer> build = buildText == null ? Optional.<Integer>empty()
                : Optional.of(Integer.parseInt(buildText));
        Optional<String> optional = Optional.ofNullable(m.group(5));
        if (!build.isPresent()) {
            if (m.group(3) != null) {
                if (optional.isPresent()) {
                    if (pre.isPresent()) {
                        throw new IllegalArgumentException("'+' found with pre-release and optional components:'" + s + "'");
                    }
                } else {
                    throw new IllegalArgumentException("'+' found with neither build or optional components: '" + s + "'");
                }
            } else if (optional.isPresent() && !pre.isPresent()) {
                throw new IllegalArgumentException("optional component must be preceded by a pre-release component or '+': '" + s + "'");
            }
        }
        return new RuntimeVersion(version, pre, build, optional);
    }

    public int feature() {
        return element(0);
    }

    public int interim() {
        return element(1);
    }

    public int update() {
        return element(2);
    }

    public int patch() {
        return element(3);
    }

    /** @deprecated as in the JDK; {@link #feature()}. */
    @Deprecated
    public int major() {
        return feature();
    }

    /** @deprecated as in the JDK; {@link #interim()}. */
    @Deprecated
    public int minor() {
        return interim();
    }

    /** @deprecated as in the JDK; {@link #update()}. */
    @Deprecated
    public int security() {
        return update();
    }

    public List<Integer> version() {
        return version;
    }

    public Optional<String> pre() {
        return pre;
    }

    public Optional<Integer> build() {
        return build;
    }

    public Optional<String> optional() {
        return optional;
    }

    @Override
    public int compareTo(RuntimeVersion obj) {
        return compare(obj, false);
    }

    public int compareToIgnoreOptional(RuntimeVersion obj) {
        return compare(obj, true);
    }

    private int compare(RuntimeVersion obj, boolean ignoreOptional) {
        if (obj == null) {
            throw new NullPointerException();
        }
        int size = Math.min(version.size(), obj.version.size());
        for (int i = 0; i < size; i++) {
            int a = version.get(i);
            int b = obj.version.get(i);
            if (a != b) {
                return a - b;
            }
        }
        if (version.size() != obj.version.size()) {
            return version.size() - obj.version.size();
        }
        int ret = comparePre(obj);
        if (ret != 0) {
            return ret;
        }
        ret = compareBuild(obj);
        if (ret != 0) {
            return ret;
        }
        return ignoreOptional ? 0 : compareOptional(obj);
    }

    private int comparePre(RuntimeVersion obj) {
        if (!pre.isPresent()) {
            return obj.pre.isPresent() ? 1 : 0;
        }
        if (!obj.pre.isPresent()) {
            return -1;
        }
        String a = pre.get();
        String b = obj.pre.get();
        boolean aDigits = DIGITS.matcher(a).matches();
        boolean bDigits = DIGITS.matcher(b).matches();
        if (aDigits) {
            return bDigits ? new BigInteger(a).compareTo(new BigInteger(b)) : -1;
        }
        return bDigits ? 1 : a.compareTo(b);
    }

    private int compareBuild(RuntimeVersion obj) {
        if (build.isPresent()) {
            return obj.build.isPresent() ? build.get().compareTo(obj.build.get()) : 1;
        }
        return obj.build.isPresent() ? -1 : 0;
    }

    private int compareOptional(RuntimeVersion obj) {
        if (!optional.isPresent()) {
            return obj.optional.isPresent() ? -1 : 0;
        }
        return obj.optional.isPresent() ? optional.get().compareTo(obj.optional.get()) : 1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < version.size(); i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(version.get(i));
        }
        if (pre.isPresent()) {
            sb.append('-').append(pre.get());
        }
        if (build.isPresent()) {
            sb.append('+').append(build.get());
            if (optional.isPresent()) {
                sb.append('-').append(optional.get());
            }
        } else if (optional.isPresent()) {
            sb.append(pre.isPresent() ? "-" : "+-").append(optional.get());
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        return equalsIgnoreOptional(obj) && optional.equals(((RuntimeVersion) obj).optional);
    }

    public boolean equalsIgnoreOptional(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RuntimeVersion)) {
            return false;
        }
        RuntimeVersion that = (RuntimeVersion) obj;
        return version.equals(that.version) && pre.equals(that.pre) && build.equals(that.build);
    }

    @Override
    public int hashCode() {
        int h = 1;
        int p = 17;
        h = p * h + version.hashCode();
        h = p * h + pre.hashCode();
        h = p * h + build.hashCode();
        h = p * h + optional.hashCode();
        return h;
    }

    private int element(int index) {
        return version.size() > index ? version.get(index) : 0;
    }
}
