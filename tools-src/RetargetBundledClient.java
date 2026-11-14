import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import net.runelite.mp.compat.JdkRetargeter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Applies the runtime JDK compatibility table to the Android copy of the bundled client. */
public final class RetargetBundledClient {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("input.jar output.jar");
        Path input = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        if (input.equals(output)) throw new IllegalArgumentException("Do not overwrite the desktop jar");
        Files.createDirectories(output.getParent());
        Path partial = Files.createTempFile(output.getParent(), "retarget-", ".jar");
        int changed = 0;
        try {
            try (ZipFile zip = new ZipFile(input.toFile());
                 ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(partial))) {
                List<String> names = zip.stream().filter(e -> !e.isDirectory())
                    .map(ZipEntry::getName).sorted().collect(Collectors.toList());
                Set<String> classes = names.stream().filter(n -> n.endsWith(".class"))
                    .map(n -> n.substring(0, n.length() - 6)).collect(Collectors.toSet());
                for (String name : names) {
                    // Signatures describe the original bytecode, not this Android derivative.
                    String upper = name.toUpperCase(java.util.Locale.ROOT);
                    if (upper.equals("META-INF/MANIFEST.MF") || upper.equals("META-INF/INDEX.LIST")
                        || upper.startsWith("META-INF/") && (upper.endsWith(".SF")
                            || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC"))) continue;
                    byte[] original;
                    try (var stream = zip.getInputStream(zip.getEntry(name))) { original = stream.readAllBytes(); }
                    byte[] bytes = JdkRetargeter.rewrite(name, original, classes, false);
                    if (name.equals("net/runelite/client/RuneLite.class")) bytes = adaptPluginStartup(bytes);
                    if (bytes != original) {
                        if (!publicApi(original).equals(publicApi(bytes))) {
                            throw new IOException("Retargeting changes the public client API: " + name);
                        }
                        // Discard unused original constant-pool entries so the build audit
                        // sees only the rewritten references, including method handles.
                        ClassWriter compact = new ClassWriter(0);
                        new ClassReader(bytes).accept(compact, 0);
                        bytes = compact.toByteArray();
                        changed++;
                    }
                    ZipEntry entry = new ZipEntry(name);
                    entry.setTime(0);
                    out.putNextEntry(entry);
                    out.write(bytes);
                    out.closeEntry();
                }
            }
            Files.move(partial, output, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(partial); }
        System.out.println("Bundled client: retargeted " + changed + " classes for Android");
    }

    /** Keep Android's DEX discovery at the desktop lifecycle point, before external plugins. */
    private static byte[] adaptPluginStartup(byte[] bytes) throws IOException {
        ClassWriter writer = new ClassWriter(0);
        int[] hooks = new int[2];
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                       String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("start") || !descriptor.equals("()V")) return method;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override public void visitMethodInsn(int opcode, String owner, String name,
                                                          String descriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC && owner.equals("net/runelite/mp/PluginBootstrap")
                            && descriptor.equals("(Lnet/runelite/client/plugins/PluginManager;)V")
                            && (name.equals("loadCorePlugins") || name.equals("startPlugins"))) {
                            hooks[name.equals("loadCorePlugins") ? 0 : 1]++;
                        }
                        if (opcode == Opcodes.INVOKEVIRTUAL
                            && owner.equals("net/runelite/client/plugins/PluginManager")
                            && descriptor.equals("()V")
                            && (name.equals("loadCorePlugins") || name.equals("startPlugins"))) {
                            hooks[name.equals("loadCorePlugins") ? 0 : 1]++;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "net/runelite/mp/PluginBootstrap", name,
                                "(Lnet/runelite/client/plugins/PluginManager;)V", false);
                        } else super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                };
            }
        }, 0);
        if (hooks[0] != 1 || hooks[1] != 1)
            throw new IOException("RuneLite startup changed: expected one core-load and one start hook, got "
                + hooks[0] + "/" + hooks[1]);
        return writer.toByteArray();
    }

    private static Set<String> publicApi(byte[] bytes) {
        Set<String> surface = new TreeSet<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public void visit(int version, int access, String name, String signature,
                                        String parent, String[] interfaces) {
                surface.add("class " + access + " " + name + " " + signature + " " + parent
                    + " " + java.util.Arrays.toString(interfaces));
            }
            @Override public FieldVisitor visitField(int access, String name, String descriptor,
                                                      String signature, Object value) {
                if ((access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0)
                    surface.add("field " + access + " " + name + descriptor + " " + signature);
                return null;
            }
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                        String signature, String[] exceptions) {
                if ((access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0)
                    surface.add("method " + access + " " + name + descriptor + " " + signature
                        + " " + java.util.Arrays.toString(exceptions));
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return surface;
    }
}
