import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import net.runelite.mp.plugins.PluginClassTransform;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import static org.objectweb.asm.Opcodes.*;

/**
 * Host side of test-runtime-plugin-dex.py.
 *
 * <pre>
 * condy DIR            write IndyCondyFixture.class: one ldc per ConstantBootstraps kind
 * selfcondy DIR        write IndySelfCondyFixture.class: a dynamic constant whose bootstrap is a
 *                      private static of the class itself (the JaCoCo $jacocoData shape)
 * transform IN OUT     run PluginClassTransform over every entry, exactly as PluginDexer does
 * </pre>
 */
public class TestIndyFixtures {
    private static final String CB = "java/lang/invoke/ConstantBootstraps";
    private static final String L = "Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;";
    private static final String NAME = "IndyCondyFixture";
    private static final String SELF = "IndySelfCondyFixture";

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "condy":
                Files.write(Paths.get(args[1], NAME + ".class"), condyFixture());
                break;
            case "selfcondy":
                Files.write(Paths.get(args[1], SELF + ".class"), selfCondyFixture());
                break;
            case "transform":
                transform(Paths.get(args[1]), Paths.get(args[2]));
                break;
            default:
                throw new IllegalArgumentException(args[0]);
        }
    }

    private static void transform(Path in, Path out) throws IOException {
        int changed = 0;
        try (ZipFile zip = new ZipFile(in.toFile());
             ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(out))) {
            Set<String> classes = new HashSet<>();
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                String name = e.nextElement().getName();
                if (name.endsWith(".class")) classes.add(name.substring(0, name.length() - ".class".length()));
            }
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                if (entry.isDirectory()) continue;
                byte[] bytes;
                try (InputStream stream = zip.getInputStream(entry)) {
                    bytes = readAll(stream);
                }
                byte[] result = PluginClassTransform.rewrite(entry.getName(), bytes, classes);
                if (result != bytes) changed++;
                output.putNextEntry(new ZipEntry(entry.getName()));
                output.write(result);
                output.closeEntry();
            }
        }
        System.out.println("PluginClassTransform rewrote " + changed + " classes");
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private static ConstantDynamic condy(String name, String desc, String bootstrap, String bootstrapDesc, Object... args) {
        return new ConstantDynamic(name, desc, new Handle(H_INVOKESTATIC, CB, bootstrap, bootstrapDesc, false), args);
    }

    private static byte[] condyFixture() {
        String invoke = "(" + L + "Ljava/lang/invoke/MethodHandle;[Ljava/lang/Object;)Ljava/lang/Object;";
        ConstantDynamic longClass = condy("J", "Ljava/lang/Class;", "primitiveClass", "(" + L + ")Ljava/lang/Class;");
        ConstantDynamic once = condy("once", "I", "invoke", invoke, new Handle(H_INVOKESTATIC, NAME, "next", "()I", false));
        // Printed in order; each is the value the JVM's resolution of the constant produces.
        Object[] constants = {
            condy("_", "Ljava/lang/String;", "nullConstant", "(" + L + ")Ljava/lang/Object;"),
            condy("I", "Ljava/lang/Class;", "primitiveClass", "(" + L + ")Ljava/lang/Class;"),
            condy("SECONDS", "Ljava/util/concurrent/TimeUnit;", "enumConstant", "(" + L + ")Ljava/lang/Enum;"),
            condy("MAX_VALUE", "I", "getStaticFinal", "(" + L + "Ljava/lang/Class;)Ljava/lang/Object;", Type.getType(Integer.class)),
            condy("TRUE", "Ljava/lang/Boolean;", "getStaticFinal", "(" + L + ")Ljava/lang/Object;"),
            condy("_", "Ljava/lang/Integer;", "invoke", invoke,
                new Handle(H_INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false), 42),
            condy("_", "Ljava/lang/CharSequence;", "invoke", invoke,
                new Handle(H_NEWINVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false), "built"),
            condy("_", "Ljava/lang/String;", "invoke", invoke,
                new Handle(H_INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false), longClass),
            condy("_", "J", "explicitCast", "(" + L + "Ljava/lang/Object;)Ljava/lang/Object;", 7),
        };

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V11, ACC_PUBLIC | ACC_SUPER, NAME, null, "java/lang/Object", null);
        cw.visitField(ACC_PRIVATE | ACC_STATIC, "calls", "I", null, null).visitEnd();

        // private static int next() { return ++calls; } -- counts bootstrap evaluations.
        MethodVisitor next = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, "next", "()I", null, null);
        next.visitCode();
        next.visitFieldInsn(GETSTATIC, NAME, "calls", "I");
        next.visitInsn(ICONST_1);
        next.visitInsn(IADD);
        next.visitInsn(DUP);
        next.visitFieldInsn(PUTSTATIC, NAME, "calls", "I");
        next.visitInsn(IRETURN);
        next.visitMaxs(0, 0);
        next.visitEnd();

        MethodVisitor onceMethod = cw.visitMethod(ACC_STATIC, "once", "()I", null, null);
        onceMethod.visitCode();
        onceMethod.visitLdcInsn(once);
        onceMethod.visitInsn(IRETURN);
        onceMethod.visitMaxs(0, 0);
        onceMethod.visitEnd();

        MethodVisitor main = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        main.visitCode();
        for (Object constant : constants) {
            main.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            main.visitLdcInsn(constant);
            box(main, ((ConstantDynamic) constant).getDescriptor());
            main.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
        }
        // Two uses of one constant, then the evaluation count: 1, 1, 1 when it resolves once.
        for (int i = 0; i < 2; i++) {
            main.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            main.visitMethodInsn(INVOKESTATIC, NAME, "once", "()I", false);
            main.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
        }
        main.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        main.visitFieldInsn(GETSTATIC, NAME, "calls", "I");
        main.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
        main.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        main.visitLdcInsn("PASS: ConstantBootstraps");
        main.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
        main.visitInsn(RETURN);
        main.visitMaxs(0, 0);
        main.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] selfCondyFixture() {
        // No static arguments and a holder-class bootstrap, the simplest shape there is; D8 9.5
        // still refuses it, so IndyLowering calls the bootstrap itself.
        String bootstrapDesc = "(" + L + ")Ljava/lang/String;";
        ConstantDynamic value = new ConstantDynamic("greeting", "Ljava/lang/String;",
            new Handle(H_INVOKESTATIC, SELF, "$bootstrap", bootstrapDesc, false));

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V11, ACC_PUBLIC | ACC_SUPER, SELF, null, "java/lang/Object", null);
        cw.visitField(ACC_PRIVATE | ACC_STATIC, "calls", "I", null, null).visitEnd();

        // private static String $bootstrap(Lookup, String name, Class type) { calls++; return name + ":" + type.getName(); }
        MethodVisitor bsm = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, "$bootstrap", bootstrapDesc, null, null);
        bsm.visitCode();
        bsm.visitFieldInsn(GETSTATIC, SELF, "calls", "I");
        bsm.visitInsn(ICONST_1);
        bsm.visitInsn(IADD);
        bsm.visitFieldInsn(PUTSTATIC, SELF, "calls", "I");
        bsm.visitTypeInsn(NEW, "java/lang/StringBuilder");
        bsm.visitInsn(DUP);
        bsm.visitVarInsn(ALOAD, 1);
        bsm.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
        bsm.visitLdcInsn(":");
        bsm.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        bsm.visitVarInsn(ALOAD, 2);
        bsm.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
        bsm.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        bsm.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        bsm.visitInsn(ARETURN);
        bsm.visitMaxs(0, 0);
        bsm.visitEnd();

        MethodVisitor get = cw.visitMethod(ACC_STATIC, "get", "()Ljava/lang/String;", null, null);
        get.visitCode();
        get.visitLdcInsn(value);
        get.visitInsn(ARETURN);
        get.visitMaxs(0, 0);
        get.visitEnd();

        // Two uses, then the evaluation count: greeting:java.lang.String twice and 1 when it resolves once.
        MethodVisitor main = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        main.visitCode();
        for (int i = 0; i < 2; i++) {
            main.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            main.visitMethodInsn(INVOKESTATIC, SELF, "get", "()Ljava/lang/String;", false);
            main.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        }
        main.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        main.visitFieldInsn(GETSTATIC, SELF, "calls", "I");
        main.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
        main.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        main.visitLdcInsn("PASS: self-bootstrapped dynamic constant");
        main.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        main.visitInsn(RETURN);
        main.visitMaxs(0, 0);
        main.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void box(MethodVisitor mv, String desc) {
        switch (desc) {
            case "I": mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false); break;
            case "J": mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false); break;
            default: break;
        }
    }
}
