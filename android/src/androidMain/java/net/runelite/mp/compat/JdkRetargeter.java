package net.runelite.mp.compat;

import java.util.Map;
import java.util.Set;
import net.runelite.mp.compat.jdk.JdkRetargets;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.H_INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.H_INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

/**
 * The one ASM pass that moves desktop-compiled bytecode off JDK members ART lacks: every call
 * (and method handle) matching a {@link JdkRetargets} row becomes a static call to its shim,
 * the receiver first; the table's relocated types are renamed to their stand-ins; and, for
 * hub jars, {@code java.util.regex} is relocated to the desktop engine in
 * {@code net.runelite.mp.compat.regex} with the {@code String} regex methods routed through
 * {@code DesktopRegexStrings}, because ART's ICU regex differs from the JDK's in syntax and
 * results. {@code ClientTransforms} runs it over the injected client without the regex
 * relocation (the client crosses into runelite-api with JDK regex types); {@code PluginDexer}
 * runs it over hub jars with it.
 *
 * <p>No rewrite changes the operand stack, so frames and max values carry over unchanged.
 */
public final class JdkRetargeter {
    private static final String PLATFORM_REGEX = "java/util/regex/";
    private static final String DESKTOP_REGEX = "net/runelite/mp/compat/regex/";
    private static final String REGEX_STRINGS = "net/runelite/mp/plugins/DesktopRegexStrings";

    private JdkRetargeter() {
    }

    /** {@link #rewrite(String, byte[], Set, boolean, int)} with no reader flags. */
    public static byte[] rewrite(String entry, byte[] bytes, Set<String> jarClasses, boolean relocateRegex) {
        return rewrite(entry, bytes, jarClasses, relocateRegex, 0);
    }

    /**
     * Rewrites one jar entry. Non-class entries and classes with nothing to rewrite come back as
     * the same array, unless {@code readerFlags} asks the reader to drop something, in which
     * case the class is always rewritten.
     *
     * @param jarClasses internal names of the classes in the same jar; a relocated type may only
     *                   cross into those
     * @throws IllegalArgumentException when a relocated type appears in the descriptor of a
     *                                  member outside the jar, which would no longer link
     */
    public static byte[] rewrite(String entry, byte[] bytes, Set<String> jarClasses, boolean relocateRegex,
                                 int readerFlags) {
        if (!entry.endsWith(".class")) {
            return bytes;
        }
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        Pass pass = new Pass(writer, jarClasses, relocateRegex);
        reader.accept(pass.entry, readerFlags);
        return pass.changed || readerFlags != 0 ? writer.toByteArray() : bytes;
    }

    private static final class Pass {
        private final Set<String> jarClasses;
        private final boolean relocateRegex;
        private final Map<String, String> relocated = JdkRetargets.relocatedTypes();
        boolean changed;
        final ClassVisitor entry;

        Pass(ClassWriter writer, Set<String> jarClasses, boolean relocateRegex) {
            this.jarClasses = jarClasses;
            this.relocateRegex = relocateRegex;
            ClassVisitor mapped = new ClassRemapper(writer, new Remapper() {
                @Override
                public String map(String name) {
                    String target = relocate(name);
                    if (target != null) {
                        changed = true;
                        return target;
                    }
                    return name;
                }

                @Override
                public Object mapValue(Object value) {
                    if (value instanceof Handle) {
                        Handle handle = (Handle) value;
                        Handle shim = retarget(handle);
                        if (shim != null) {
                            changed = true;
                            return super.mapValue(shim);
                        }
                        checkBoundary(handle.getOwner(), handle.getDesc());
                    }
                    return super.mapValue(value);
                }
            });
            this.entry = new ClassVisitor(ASM9, mapped) {
                @Override
                public void visitInnerClass(String name, String outerName, String innerName, int access) {
                    // Runtime$Version and the like stop being nested JDK classes once renamed.
                    if (relocated.containsKey(name)) {
                        changed = true;
                        return;
                    }
                    super.visitInnerClass(name, outerName, innerName, access);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                                 String[] exceptions) {
                    return new MethodVisitor(ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
                            JdkRetargets.Entry row = row(kindOf(opcode), owner, method, desc);
                            if (row != null) {
                                changed = true;
                                super.visitMethodInsn(INVOKESTATIC, row.shimOwner, row.shimName, row.shimDescriptor, false);
                                return;
                            }
                            if (opcode == INVOKEVIRTUAL && relocateRegex && stringRegex(owner, method, desc)) {
                                changed = true;
                                super.visitMethodInsn(INVOKESTATIC, REGEX_STRINGS, method,
                                        "(Ljava/lang/String;" + desc.substring(1), false);
                                return;
                            }
                            checkBoundary(owner, desc);
                            super.visitMethodInsn(opcode, owner, method, desc, itf);
                        }

                        @Override
                        public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                            checkBoundary(owner, desc);
                            super.visitFieldInsn(opcode, owner, field, desc);
                        }
                    };
                }
            };
        }

        private String relocate(String name) {
            String target = relocated.get(name);
            if (target != null) {
                return target;
            }
            if (relocateRegex && name.startsWith(PLATFORM_REGEX)) {
                return DESKTOP_REGEX + name.substring(PLATFORM_REGEX.length());
            }
            return null;
        }

        private JdkRetargets.Entry row(int kind, String owner, String name, String descriptor) {
            if (kind < 0 || relocateRegex && owner.startsWith(PLATFORM_REGEX)) {
                // The desktop engine the regex types move to has the JDK 9+ members itself.
                return null;
            }
            return JdkRetargets.find(kind, owner, name, descriptor);
        }

        private Handle retarget(Handle handle) {
            int tag = handle.getTag();
            int kind = tag == H_INVOKESTATIC ? JdkRetargets.STATIC
                    : tag == H_INVOKEVIRTUAL ? JdkRetargets.VIRTUAL
                    : tag == H_INVOKEINTERFACE ? JdkRetargets.INTERFACE : -1;
            JdkRetargets.Entry row = row(kind, handle.getOwner(), handle.getName(), handle.getDesc());
            if (row != null) {
                return new Handle(H_INVOKESTATIC, row.shimOwner, row.shimName, row.shimDescriptor, false);
            }
            if (tag == H_INVOKEVIRTUAL && relocateRegex
                    && stringRegex(handle.getOwner(), handle.getName(), handle.getDesc())) {
                return new Handle(H_INVOKESTATIC, REGEX_STRINGS, handle.getName(),
                        "(Ljava/lang/String;" + handle.getDesc().substring(1), false);
            }
            return null;
        }

        /**
         * A member outside the jar whose descriptor names a relocated type still expects the
         * platform type; renaming it would be a linkage error, so refuse instead of guessing.
         */
        private void checkBoundary(String owner, String descriptor) {
            if (jarClasses.contains(owner) || relocate(owner) != null) {
                return;
            }
            if (relocateRegex && descriptor.contains("L" + PLATFORM_REGEX)) {
                throw new IllegalArgumentException("Desktop regex crosses host API: " + owner + descriptor);
            }
            for (String type : relocated.keySet()) {
                if (descriptor.contains("L" + type + ";")) {
                    throw new IllegalArgumentException("Relocated " + type + " crosses host API: " + owner + descriptor);
                }
            }
        }
    }

    private static int kindOf(int opcode) {
        switch (opcode) {
            case INVOKESTATIC: return JdkRetargets.STATIC;
            case INVOKEVIRTUAL: return JdkRetargets.VIRTUAL;
            case INVOKEINTERFACE: return JdkRetargets.INTERFACE;
            default: return -1;
        }
    }

    private static boolean stringRegex(String owner, String name, String descriptor) {
        if (!owner.equals("java/lang/String")) {
            return false;
        }
        switch (name) {
            case "split":
                return descriptor.equals("(Ljava/lang/String;)[Ljava/lang/String;")
                        || descriptor.equals("(Ljava/lang/String;I)[Ljava/lang/String;");
            case "matches":
                return descriptor.equals("(Ljava/lang/String;)Z");
            case "splitWithDelimiters":
                return descriptor.equals("(Ljava/lang/String;I)[Ljava/lang/String;");
            case "replaceFirst":
            case "replaceAll":
                return descriptor.equals("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
            default:
                return false;
        }
    }
}
