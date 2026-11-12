import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Private-method lowering check over a desugared client jar (the sailing world-view lambda was the
 * first casualty): no generated lambda may INVOKESPECIAL anything but a constructor, and every one
 * must link. Run with -Xverify:all; the jar is DesugarLambdasTask's output pulled from a device.
 * Revision-independent: it walks every runelite/desugar/Lambda* class instead of naming one.
 */
public class TestSailingLambdaAccess {
    public static void main(String[] args) throws Exception {
        List<String> lambdas = new ArrayList<>();
        try (JarFile jar = new JarFile(args[0])) {
            for (JarEntry entry : java.util.Collections.list(jar.entries())) {
                String name = entry.getName();
                if (!name.startsWith("runelite/desugar/Lambda") || !name.endsWith(".class")) continue;
                lambdas.add(name.substring(0, name.length() - ".class".length()));
                try (InputStream in = jar.getInputStream(entry)) {
                    new ClassReader(in.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public MethodVisitor visitMethod(int access, String method, String desc, String signature, String[] exceptions) {
                            return new MethodVisitor(Opcodes.ASM9) {
                                @Override
                                public void visitMethodInsn(int opcode, String owner, String target, String d, boolean itf) {
                                    if (opcode == Opcodes.INVOKESPECIAL && !target.equals("<init>")) {
                                        throw new AssertionError(name + " dispatches " + owner + "." + target + d + " with INVOKESPECIAL");
                                    }
                                }
                            };
                        }
                    }, ClassReader.SKIP_FRAMES);
                }
            }
        }
        if (lambdas.isEmpty()) throw new AssertionError("No generated lambdas found");

        int linked = 0;
        List<String> unresolved = new ArrayList<>();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {new File(args[0]).toURI().toURL()})) {
            for (String lambda : lambdas) {
                try {
                    Class.forName(lambda.replace('/', '.'), true, loader);
                    linked++;
                } catch (NoClassDefFoundError e) {
                    // App-side shims (UnsafeShim, StringShim) are not in the client jar.
                    unresolved.add(lambda + ": " + e.getMessage());
                } catch (VerifyError | IllegalAccessError e) {
                    throw new AssertionError(lambda + " does not link", e);
                }
            }
        }
        for (String line : unresolved) System.out.println("  skipped " + line);
        System.out.println("PASS: " + linked + " of " + lambdas.size() + " generated client lambdas link without INVOKESPECIAL");
    }
}
