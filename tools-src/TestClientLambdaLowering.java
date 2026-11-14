import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.runelite.mp.client.SyntheticLambdas;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import static org.objectweb.asm.Opcodes.*;

/**
 * Regression test for the client lambda pass's private-method lowering (DesugarLambdasTask):
 * a metafactory site whose implementation is an H_INVOKESPECIAL handle must become a synthetic
 * class that dispatches it with INVOKEVIRTUAL, never INVOKESPECIAL, since the synthetic is not a
 * subclass of the owner. Arguments: IN_DIR (compiled ClientLambdaFixture) OUT_DIR.
 *
 * <p>The synthetic classes come from SyntheticLambdas, the generator the task itself calls; the
 * call-site rewrite and the access widening here mirror DesugarLambdasTask.rewriteClass and
 * LambdaRewriter. The driver then runs OUT_DIR under -Xverify:all, where a stray INVOKESPECIAL is
 * a VerifyError, and dexes it.
 */
public class TestClientLambdaLowering {
    private static final String PREFIX = "runelite/desugar/Lambda";

    public static void main(String[] args) throws Exception {
        Path in = Paths.get(args[0]);
        Path out = Paths.get(args[1]);
        Map<String, byte[]> classes = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(in)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                String name = in.relativize(file).toString().replace('\\', '/');
                classes.put(name.substring(0, name.length() - ".class".length()), Files.readAllBytes(file));
            }
        }

        // Private instance methods, so an H_INVOKEVIRTUAL handle javac might emit for one is
        // pinned to the H_INVOKESPECIAL shape the obfuscated client has.
        Set<String> privateInstance = new HashSet<>();
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            String owner = entry.getKey();
            new ClassReader(entry.getValue()).accept(new ClassVisitor(ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if ((access & ACC_PRIVATE) != 0 && (access & ACC_STATIC) == 0 && !name.equals("<init>")) {
                        privateInstance.add(owner + "." + name + desc);
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE);
        }

        // First pass of DesugarLambdasTask: every impl method and its owner get widened.
        Set<String> widen = new HashSet<>();
        Set<String> widenClasses = new HashSet<>();
        for (byte[] bytes : classes.values()) {
            new ClassReader(bytes).accept(new ClassVisitor(ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    return new MethodVisitor(ASM9) {
                        @Override
                        public void visitInvokeDynamicInsn(String n, String d, Handle bsm, Object... bsmArgs) {
                            if (metafactory(bsm)) {
                                Handle impl = (Handle) bsmArgs[1];
                                widen.add(impl.getOwner() + "." + impl.getName() + impl.getDesc());
                                widenClasses.add(impl.getOwner());
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_FRAMES);
        }

        List<String[]> report = new ArrayList<>();
        Map<String, byte[]> synthetics = new LinkedHashMap<>();
        int[] special = {0};
        Map<String, byte[]> rewritten = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            String owner = entry.getKey();
            ClassReader reader = new ClassReader(entry.getValue());
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
                @Override
                protected String getCommonSuperClass(String a, String b) {
                    try {
                        return super.getCommonSuperClass(a, b);
                    } catch (Throwable t) {
                        return "java/lang/Object";
                    }
                }
            };
            reader.accept(new ClassVisitor(ASM9, writer) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    super.visit(version, widenClasses.contains(name) ? publicAccess(access) : access, name, signature, superName, interfaces);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    int effective = widen.contains(owner + "." + name + desc) ? publicAccess(access) : access;
                    MethodVisitor mv = super.visitMethod(effective, name, desc, signature, exceptions);
                    return new GeneratorAdapter(ASM9, mv, effective, name, desc) {
                        @Override
                        public void visitInvokeDynamicInsn(String n, String d, Handle bsm, Object... bsmArgs) {
                            if (!metafactory(bsm)) {
                                super.visitInvokeDynamicInsn(n, d, bsm, bsmArgs);
                                return;
                            }
                            Handle impl = (Handle) bsmArgs[1];
                            if (impl.getTag() == H_INVOKEVIRTUAL && privateInstance.contains(impl.getOwner() + "." + impl.getName() + impl.getDesc())) {
                                impl = new Handle(H_INVOKESPECIAL, impl.getOwner(), impl.getName(), impl.getDesc(), impl.isInterface());
                            }
                            if (impl.getTag() == H_INVOKESPECIAL) special[0]++;
                            Type[] captures = Type.getArgumentTypes(d);
                            String captureDesc = Type.getMethodDescriptor(Type.VOID_TYPE, captures);
                            String syn = PREFIX + synthetics.size();
                            synthetics.put(syn, SyntheticLambdas.generate(syn, d, n, (Type) bsmArgs[0], (Type) bsmArgs[2], impl, captureDesc));
                            report.add(new String[] {syn, impl.getTag() == H_INVOKESPECIAL ? "H_INVOKESPECIAL" : "tag " + impl.getTag(), impl.getName() + impl.getDesc()});

                            int[] locals = new int[captures.length];
                            for (int i = captures.length - 1; i >= 0; i--) {
                                locals[i] = newLocal(captures[i]);
                                storeLocal(locals[i], captures[i]);
                            }
                            visitTypeInsn(NEW, syn);
                            visitInsn(DUP);
                            for (int i = 0; i < captures.length; i++) loadLocal(locals[i], captures[i]);
                            visitMethodInsn(INVOKESPECIAL, syn, "<init>", captureDesc, false);
                        }
                    };
                }
            }, ClassReader.EXPAND_FRAMES);
            rewritten.put(owner, writer.toByteArray());
        }

        // The regression: a synthetic may only INVOKESPECIAL a constructor.
        for (Map.Entry<String, byte[]> entry : synthetics.entrySet()) {
            String syn = entry.getKey();
            new ClassReader(entry.getValue()).accept(new ClassVisitor(ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    return new MethodVisitor(ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String method, String d, boolean itf) {
                            if (opcode == INVOKESPECIAL && !method.equals("<init>")) {
                                throw new AssertionError(syn + " dispatches " + owner + "." + method + d + " with INVOKESPECIAL");
                            }
                        }
                    };
                }
            }, 0);
        }
        if (synthetics.isEmpty()) throw new AssertionError("no metafactory sites in the fixture");
        if (special[0] == 0) throw new AssertionError("no H_INVOKESPECIAL implementation handle reached the lowering");

        rewritten.putAll(synthetics);
        for (Map.Entry<String, byte[]> entry : rewritten.entrySet()) {
            Path file = out.resolve(entry.getKey() + ".class");
            Files.createDirectories(file.getParent());
            Files.write(file, entry.getValue());
        }
        for (String[] line : report) System.out.println("  " + line[0] + ": " + line[1] + " " + line[2]);
        System.out.println("lowered " + synthetics.size() + " metafactory sites, " + special[0] + " through H_INVOKESPECIAL");
    }

    private static boolean metafactory(Handle bsm) {
        return bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory") && bsm.getName().equals("metafactory");
    }

    private static int publicAccess(int access) {
        return (access & ~(ACC_PRIVATE | ACC_PROTECTED)) | ACC_PUBLIC;
    }
}
