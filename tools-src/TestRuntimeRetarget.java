import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import net.runelite.mp.compat.JdkRetargeter;
import net.runelite.mp.compat.jdk.JdkRetargets;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Host half of the retargeting fixtures: {@code TestRuntimeRetarget <plugin.jar> <out.jar>} runs
 * JdkRetargeter over a jar the way PluginDexer does (hub mode, regex relocated), then checks the
 * result: no call or method handle still names a table row, a relocated type or one of String's
 * regex methods (those go to DesktopRegexStrings), the shims the
 * fixture needs are called, a second pass changes nothing, and the client mode leaves regex alone.
 */
public final class TestRuntimeRetarget {
    private static final String[] EXPECTED_SHIMS = {
        "JdkStrings.repeat", "JdkStrings.strip", "JdkStrings.stripLeading", "JdkStrings.stripTrailing",
        "JdkStrings.isBlank", "JdkStrings.lines", "JdkFiles.readString", "JdkFiles.writeString",
        "JdkRuntime.version", "JdkConcurrent.keySet", "DesktopRegexStrings.split",
        "DesktopRegexStrings.matches", "DesktopRegexStrings.replaceAll", "DesktopRegexStrings.replaceFirst",
    };

    /** String's regex methods, which hub mode routes to DesktopRegexStrings with the receiver first. */
    private static final String[][] STRING_REGEX = {
        {"split", "(Ljava/lang/String;)[Ljava/lang/String;"},
        {"split", "(Ljava/lang/String;I)[Ljava/lang/String;"},
        {"matches", "(Ljava/lang/String;)Z"},
        {"splitWithDelimiters", "(Ljava/lang/String;I)[Ljava/lang/String;"},
        {"replaceFirst", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"},
        {"replaceAll", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"},
    };
    private static final String REGEX_STRINGS = "net/runelite/mp/plugins/DesktopRegexStrings";

    public static void main(String[] args) throws IOException {
        checkTable();
        checkClientMode();
        checkStringRegex();
        Set<String> referenced = new TreeSet<>();
        List<String> leftovers = new ArrayList<>();
        int changed = 0;
        try (ZipFile input = new ZipFile(args[0]);
             OutputStream file = Files.newOutputStream(Path.of(args[1]));
             ZipOutputStream output = new ZipOutputStream(file)) {
            Set<String> classes = new HashSet<>();
            input.stream().filter(e -> e.getName().endsWith(".class"))
                    .forEach(e -> classes.add(e.getName().substring(0, e.getName().length() - 6)));
            for (ZipEntry entry : java.util.Collections.list(input.entries())) {
                byte[] bytes;
                try (InputStream in = input.getInputStream(entry)) {
                    bytes = in.readAllBytes();
                }
                byte[] result = JdkRetargeter.rewrite(entry.getName(), bytes, classes, true);
                if (result != bytes) {
                    changed++;
                }
                if (!Arrays.equals(result, JdkRetargeter.rewrite(entry.getName(), result, classes, true))) {
                    throw new AssertionError("second pass changed " + entry.getName());
                }
                if (entry.getName().endsWith(".class")) {
                    scan(result, referenced, leftovers, true);
                }
                output.putNextEntry(new ZipEntry(entry.getName()));
                output.write(result);
                output.closeEntry();
            }
        }
        if (!leftovers.isEmpty()) {
            throw new AssertionError("unretargeted JDK members: " + leftovers);
        }
        for (String shim : EXPECTED_SHIMS) {
            if (!referenced.contains(shim)) {
                throw new AssertionError("fixture does not call " + shim + " after retargeting; calls " + referenced);
            }
        }
        System.out.println("PASS: retargeted " + changed + " classes; shims " + referenced);
    }

    /** Every row's shim descriptor is the JDK member's with the receiver first for instance calls. */
    private static void checkTable() {
        for (JdkRetargets.Entry row : JdkRetargets.entries()) {
            if (!row.shimOwner.startsWith("net/runelite/mp/compat/jdk/")) {
                throw new AssertionError("shim outside compat.jdk: " + row);
            }
            String expected = row.kind == JdkRetargets.STATIC ? row.descriptor
                    : "(L" + (row.kind == JdkRetargets.INTERFACE ? "java/lang/Object" : row.owner) + ";"
                    + row.descriptor.substring(1);
            String returned = row.descriptor.substring(row.descriptor.indexOf(')') + 1);
            for (String type : JdkRetargets.relocatedTypes().keySet()) {
                expected = expected.replace("L" + type + ";", "L" + JdkRetargets.relocatedTypes().get(type) + ";");
            }
            if (!row.shimDescriptor.equals(expected)) {
                throw new AssertionError("shim descriptor " + row.shimDescriptor + " expected " + expected + " (returns " + returned + ")");
            }
        }
    }

    /** The client crosses into runelite-api with JDK regex types, so it keeps them. */
    private static void checkClientMode() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "ClientFixture", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test",
                "(Ljava/util/regex/Matcher;Ljava/lang/String;)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/regex/Matcher", "replaceAll",
                "(Ljava/util/function/Function;)Ljava/lang/String;", false);
        method.visitInsn(Opcodes.POP);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitInsn(Opcodes.ICONST_2);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "repeat", "(I)Ljava/lang/String;", false);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 2);
        method.visitEnd();
        writer.visitEnd();
        byte[] result = JdkRetargeter.rewrite("ClientFixture.class", writer.toByteArray(), Set.of("ClientFixture"), false);
        Set<String> referenced = new TreeSet<>();
        List<String> leftovers = new ArrayList<>();
        scan(result, referenced, leftovers, false);
        if (!leftovers.isEmpty() || !referenced.containsAll(List.of("JdkRegex.replaceAll", "JdkStrings.repeat"))) {
            throw new AssertionError("client mode: leftovers " + leftovers + ", shims " + referenced);
        }
        String text = new String(result, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (!text.contains("java/util/regex/Matcher") || text.contains("net/runelite/mp/compat/regex/")) {
            throw new AssertionError("client mode relocated java.util.regex");
        }
    }

    /**
     * Every String regex method, called with INVOKEVIRTUAL, must become INVOKESTATIC on
     * DesktopRegexStrings with the receiver prepended in hub mode (splitWithDelimiters included,
     * which a --release 11 fixture cannot call), and stay on String in client mode.
     */
    private static void checkStringRegex() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "StringRegexFixture", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test",
                "(Ljava/lang/String;)V", null, null);
        method.visitCode();
        for (String[] member : STRING_REGEX) {
            method.visitVarInsn(Opcodes.ALOAD, 0);
            // One String or int per parameter; only the descriptor matters to the rewrite.
            for (char parameter : member[1].substring(1, member[1].indexOf(')')).replace("Ljava/lang/String;", "S").toCharArray()) {
                if (parameter == 'S') {
                    method.visitLdcInsn("x");
                } else {
                    method.visitInsn(Opcodes.ICONST_0);
                }
            }
            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", member[0], member[1], false);
            method.visitInsn(Opcodes.POP);
        }
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(3, 1);
        method.visitEnd();
        writer.visitEnd();
        byte[] original = writer.toByteArray();

        List<String> hub = calls(JdkRetargeter.rewrite("StringRegexFixture.class", original,
                Set.of("StringRegexFixture"), true));
        List<String> client = calls(JdkRetargeter.rewrite("StringRegexFixture.class", original,
                Set.of("StringRegexFixture"), false));
        List<String> expectedHub = new ArrayList<>();
        List<String> expectedClient = new ArrayList<>();
        for (String[] member : STRING_REGEX) {
            expectedHub.add(Opcodes.INVOKESTATIC + " " + REGEX_STRINGS + "." + member[0]
                    + "(Ljava/lang/String;" + member[1].substring(1));
            expectedClient.add(Opcodes.INVOKEVIRTUAL + " java/lang/String." + member[0] + member[1]);
        }
        if (!hub.equals(expectedHub)) {
            throw new AssertionError("hub String regex calls " + hub + " expected " + expectedHub);
        }
        if (!client.equals(expectedClient)) {
            throw new AssertionError("client String regex calls " + client + " expected " + expectedClient);
        }
    }

    private static List<String> calls(byte[] bytes) {
        List<String> calls = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
                        calls.add(opcode + " " + owner + "." + method + desc);
                    }
                };
            }
        }, 0);
        return calls;
    }

    private static boolean stringRegex(String owner, String method, String desc) {
        if (!owner.equals("java/lang/String")) {
            return false;
        }
        for (String[] member : STRING_REGEX) {
            if (member[0].equals(method) && member[1].equals(desc)) {
                return true;
            }
        }
        return false;
    }

    private static void scan(byte[] bytes, Set<String> referenced, List<String> leftovers, boolean hub) {
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
                        member(owner, method, desc);
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String method, String desc, Handle bsm, Object... bsmArgs) {
                        for (Object arg : bsmArgs) {
                            if (arg instanceof Handle) {
                                Handle handle = (Handle) arg;
                                member(handle.getOwner(), handle.getName(), handle.getDesc());
                            }
                        }
                    }

                    private void member(String owner, String method, String desc) {
                        for (JdkRetargets.Entry row : JdkRetargets.entries()) {
                            if (row.owner.equals(owner) && row.name.equals(method) && row.descriptor.equals(desc)) {
                                leftovers.add(owner + "." + method + desc);
                            }
                        }
                        for (String type : JdkRetargets.relocatedTypes().keySet()) {
                            if (owner.equals(type) || desc.contains("L" + type + ";")) {
                                leftovers.add(owner + "." + method + desc);
                            }
                        }
                        if (hub && stringRegex(owner, method, desc)) {
                            leftovers.add(owner + "." + method + desc);
                        }
                        int slash = owner.lastIndexOf('/');
                        if (owner.startsWith("net/runelite/mp/")) {
                            referenced.add(owner.substring(slash + 1) + "." + method);
                        }
                    }
                };
            }
        }, 0);
    }
}
